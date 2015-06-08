/**
 * Copyright (c) 2014 Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baidu.rigel.biplatform.queryrouter.queryplugin.plugins.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;

import com.baidu.rigel.biplatform.ac.minicube.MiniCube;
import com.baidu.rigel.biplatform.ac.model.Dimension;
import com.baidu.rigel.biplatform.ac.query.model.AxisMeta.AxisType;
import com.baidu.rigel.biplatform.ac.query.model.ConfigQuestionModel;
import com.baidu.rigel.biplatform.ac.query.model.DimensionCondition;
import com.baidu.rigel.biplatform.ac.query.model.MeasureCondition;
import com.baidu.rigel.biplatform.ac.query.model.MetaCondition;
import com.baidu.rigel.biplatform.ac.query.model.PageInfo;
import com.baidu.rigel.biplatform.ac.query.model.QueryData;
import com.baidu.rigel.biplatform.ac.query.model.QuestionModel;
import com.baidu.rigel.biplatform.ac.query.model.SQLCondition;
import com.baidu.rigel.biplatform.ac.util.MetaNameUtil;
import com.baidu.rigel.biplatform.queryrouter.queryplugin.plugins.common.QuestionModel4TableDataUtils;

/**
 * 
 * Description: sql数据组织类
 * 
 * @author 罗文磊
 *
 */
public class SqlExpression implements Serializable {

    /**
     * serialVersionUID
     */
    private static final long serialVersionUID = 3151301875582323398L;

    /**
     * select sql
     */
    private String sql;

    /**
     * countSql
     */
    private String countSql;

    /**
     * jdbc driver
     */
    private String driver;

    /**
     * 生成组织sql string
     * 
     * @param questionModel
     *            questionModel
     * @param allColums
     *            allColums
     * @param needColums
     *            needColums
     * @return String sql
     */
    public void generateSql(QuestionModel questionModel,
            HashMap<String, SqlColumn> allColums, List<SqlColumn> needColums) {
        ConfigQuestionModel configQuestionModel = (ConfigQuestionModel) questionModel;
        MiniCube cube = (MiniCube) configQuestionModel.getCube();
        StringBuffer sqlExpression = new StringBuffer();
        sqlExpression.append(generateSelectExpression(needColums));
        sqlExpression.append(generateFromExpression(cube));
        sqlExpression.append(generateLeftOuterJoinExpression(
                configQuestionModel, allColums, needColums));
        sqlExpression.append(generateWhereExpression(configQuestionModel,
                allColums));
        sqlExpression.append(generateOrderByExpression(configQuestionModel,
                allColums));
        // set no paged sql
        this.setSql(sqlExpression.toString());
        // set total count sql
        this.setCountSql(" select count(1) as count from (" + this.getSql()
                + ") t");
        generateTotalExpression(configQuestionModel.getPageInfo());
    }

    /**
     * 事实表物理表名
     * 
     * @param cube
     *            cube
     * @return String tablename
     */
    public String getFactTableName(MiniCube cube) {
        return cube.getSource();
    }

    /**
     * 生成select sql语句
     * 
     * @param HashMap
     *            allColums
     * @param HashMap
     *            needColums
     * @return String sql
     */
    public String generateSelectExpression(List<SqlColumn> needColums) {
        StringBuffer select = new StringBuffer("select ");
        for (SqlColumn colum : needColums) {
            String columName = colum.getTableName() + SqlConstants.DOT
                    + colum.getTableFieldName();
            String columNameSqlAlias = colum.getSqlUniqueColumn();
            if (AxisType.COLUMN == colum.getType()) {
                select.append(columName + " as " + columNameSqlAlias + SqlConstants.COMMA);
            } else if (AxisType.ROW == colum.getType()) {
                if (QuestionModel4TableDataUtils.isTimeOrCallbackDimension(colum.getDimension())) {
                    // 如果为时间维度
                    columName = colum.getFactTableName() + SqlConstants.DOT
                            + colum.getDimension().getFacttableColumn();
                }
                select.append(columName + " as " + columNameSqlAlias + SqlConstants.COMMA);
            }
        }
        return select.toString().substring(0,
                select.toString().lastIndexOf(SqlConstants.COMMA))
                + SqlConstants.SPACE;
    }

    /**
     * 生成from sql语句
     * 
     * @param MiniCube
     *            cube
     * @return String sql
     */
    public String generateFromExpression(MiniCube cube) {
        String tableName = this.getFactTableName(cube);
        return " from " + tableName + SqlConstants.SPACE + tableName + SqlConstants.SPACE;
    }

    /**
     * 生成leftouterjoin sql语句
     * 
     * @param HashMap
     *            allColums
     * @param HashMap
     *            needColums
     * 
     * @return String sql
     */
    public String generateLeftOuterJoinExpression(
            ConfigQuestionModel configQuestionModel,
            HashMap<String, SqlColumn> allColums, List<SqlColumn> needColums) {
        List<SqlColumn> needJoinColumns = new ArrayList<SqlColumn>();
        needJoinColumns.addAll(needColums);
        StringBuffer leftOuterJoinExpressions = new StringBuffer();
        HashSet<String> joinTables = new HashSet<String>();
        Set<Map.Entry<String, MetaCondition>> metaConditionSet = configQuestionModel
                .getQueryConditions().entrySet();
        MiniCube cube = (MiniCube) configQuestionModel.getCube();
        String factTableName = cube.getSource();
        for (Entry<String, MetaCondition> entry : metaConditionSet) {
            MetaCondition metaCondition = entry.getValue();
            if (entry.getValue() instanceof DimensionCondition) {
                // 判断是维度查询
                DimensionCondition dimensionCondition = (DimensionCondition) metaCondition;
                Dimension dimension = cube.getDimensions().get(
                        dimensionCondition.getMetaName());
                String demensionName = dimensionCondition.getMetaName();
                if (dimensionCondition.getQueryDataNodes() == null
                        || dimensionCondition.getQueryDataNodes().isEmpty()) {
                    // 如果节点为空，不需要组织条件
                    continue;
                }
                if (MetaNameUtil.isAllMemberUniqueName(dimensionCondition
                        .getQueryDataNodes().get(0).getUniqueName())) {
                    // 如果节点为所有条件，不需要组织条件
                    continue;
                }
                if (QuestionModel4TableDataUtils.isTimeOrCallbackDimension(dimension)) {
                    // 如果为TIME_DIMENSION，name为指标字段
                    continue;
                }
                // 判断是或否有添加查询
                SqlColumn sqlColumn = allColums.get(demensionName);
                if (sqlColumn.getTableName().equals(factTableName)) {
                    // 如果为事实表不添加到jointable中
                    continue;
                }
                needJoinColumns.add(sqlColumn);
            }
        }
        for (SqlColumn colum : needJoinColumns) {
            if (AxisType.ROW == colum.getType()) {
                String dimTableName = colum.getTableName();
                if (QuestionModel4TableDataUtils.isTimeOrCallbackDimension(colum.getDimension())) {
                    // 如果为时间维度或callback维护，不需要leftouterjoin
                    continue;
                }
                if (factTableName.equals(dimTableName)) {
                    // 如果join table为facttable,跳过
                    continue;
                }
                if (joinTables.contains(dimTableName)) {
                    // 如果join的dimTableName已在sql中存在，
                    continue;
                }
                joinTables.add(dimTableName);
                StringBuffer oneLeftOuterJoinExpression = new StringBuffer(
                        " left outer join ");
                oneLeftOuterJoinExpression.append(dimTableName + SqlConstants.SPACE
                        + dimTableName + SqlConstants.SPACE);
                String dimTableFiled = dimTableName + SqlConstants.DOT
                        + colum.getLevel().getPrimaryKey();
                String measureTableFiled = colum.getFactTableName() + SqlConstants.DOT
                        + colum.getLevel().getFactTableColumn();
                oneLeftOuterJoinExpression.append(" on " + dimTableFiled
                        + " = " + measureTableFiled + SqlConstants.SPACE);
                leftOuterJoinExpressions.append(oneLeftOuterJoinExpression);
            }
        }
        return leftOuterJoinExpressions.toString();
    }

    /**
     * 生成where sql语句
     * 
     * @param ConfigQuestionModel
     *            configQuestionModel
     * @return String sql
     */
    public String generateWhereExpression(
            ConfigQuestionModel configQuestionModel,
            HashMap<String, SqlColumn> allColums) {

        Set<Map.Entry<String, MetaCondition>> metaConditionSet = configQuestionModel
                .getQueryConditions().entrySet();
        MiniCube cube = (MiniCube) configQuestionModel.getCube();
        StringBuffer whereExpressions = new StringBuffer(" where 1=1 ");
        for (Entry<String, MetaCondition> entry : metaConditionSet) {
            MetaCondition metaCondition = entry.getValue();
            if (entry.getValue() instanceof DimensionCondition) {
                // 判断是维度查询
                DimensionCondition dimensionCondition = (DimensionCondition) metaCondition;
                Dimension dimension = cube.getDimensions().get(
                        dimensionCondition.getMetaName());
                String demensionName = dimensionCondition.getMetaName();
                if (CollectionUtils.isEmpty(dimensionCondition.getQueryDataNodes())) {
                    // 如果节点为空，不需要组织条件
                    continue;
                }
                if (MetaNameUtil.isAllMemberUniqueName(dimensionCondition
                        .getQueryDataNodes().get(0).getUniqueName())) {
                    // 如果节点为所有条件，不需要组织条件
                    continue;
                }
                if (QuestionModel4TableDataUtils.isTimeOrCallbackDimension(dimension)) {
                    // 如果为TIME_DIMENSION，name为指标字段
                    demensionName = dimension.getFacttableColumn();
                }
                // 判断是或否有添加查询
                SqlColumn sqlColumn = allColums.get(demensionName);
                String equals = "";
                String inExpression = "";
                for (QueryData queryData : dimensionCondition
                        .getQueryDataNodes()) {
                    inExpression = inExpression
                            + "\'"
                            + queryData.getUniqueName()
                                    .substring(
                                            queryData.getUniqueName()
                                                    .lastIndexOf("[") + 1,
                                            queryData.getUniqueName()
                                                    .lastIndexOf("]")) + "\',";
                }
                if (!inExpression.isEmpty()) {
                    // 如果inExpression没有值，证明没有此条件
                    String columnSqName = sqlColumn.getTableName() + SqlConstants.DOT
                            + sqlColumn.getTableFieldName();
                    equals = " in ("
                            + inExpression.substring(0,
                                    inExpression.lastIndexOf(SqlConstants.COMMA)) + ")";
                    whereExpressions.append(" and " + columnSqName + equals
                            + SqlConstants.SPACE);
                }

            } else if (entry.getValue() instanceof MeasureCondition) {
                // 判断是指标查询
                MeasureCondition measureCondition = (MeasureCondition) metaCondition;
                if (measureCondition.getMeasureConditions() != null) {
                    // 判断是或否有添加查询
                    SqlColumn sqlColumn = allColums.get(measureCondition
                            .getMetaName());
                    SQLCondition sqlCondition = measureCondition
                            .getMeasureConditions();
                    String columnSqName = sqlColumn.getTableName() + SqlConstants.DOT
                            + sqlColumn.getTableFieldName();
                    whereExpressions.append(" and " + columnSqName
                            + this.generateWhereEquals(sqlCondition) + SqlConstants.SPACE);
                }
            }
        }
        return whereExpressions.toString();
    }
    
    /**
     * 获取where equals等式
     * 
     * @param sqlCondition
     *            sqlCondition
     * @return where equals sting
     */
    private String generateWhereEquals(SQLCondition sqlCondition) {
        if (sqlCondition == null || sqlCondition.getConditionValues() == null
                || sqlCondition.getConditionValues().size() < 0) {
            return "";
        }
        return SqlWhereExpressionFactory.getWhereExpression(sqlCondition);
    }

    /**
     * 生成groupby sql语句
     * 
     * @param HashMap
     *            allColums
     * @param HashMap
     *            needColums
     * @return String sql
     */
    public String generateGroupByExpression(List<SqlColumn> needColums) {
        StringBuffer groupbyExpression = new StringBuffer(" group by ");
        for (SqlColumn colum : needColums) {
            if (AxisType.ROW == colum.getType()) {
                String columName = "";
                if (QuestionModel4TableDataUtils.isTimeOrCallbackDimension(colum.getDimension())) {
                    // 如果为时间维度
                    columName = colum.getFactTableName() + SqlConstants.DOT
                            + colum.getDimension().getFacttableColumn();
                    groupbyExpression.append(columName + SqlConstants.COMMA);
                } else {
                    // 如果为其他维度
                    String dimTableName = colum.getTableName();
                    groupbyExpression.append(dimTableName + SqlConstants.DOT
                            + colum.getTableFieldName() + SqlConstants.COMMA);
                }
            } else if (AxisType.COLUMN == colum.getType()) {
                return "";
            }
        }
        if (" group by ".equals(groupbyExpression.toString())) {
            return "";
        } else {
            return groupbyExpression.toString().substring(0,
                    groupbyExpression.toString().lastIndexOf(SqlConstants.COMMA));
        }

    }

    /**
     * 生成orderby sql语句
     * 
     * @param ConfigQuestionModel
     *            configQuestionModel
     * @param HashMap
     *            allColums
     * @return String orderby sql
     */
    public String generateOrderByExpression(
            ConfigQuestionModel configQuestionModel,
            HashMap<String, SqlColumn> allColums) {
        String orderColumnNameTmp = configQuestionModel.getSortRecord()
                .getSortColumnUniquename();
        String[] str = MetaNameUtil.parseUnique2NameArray(orderColumnNameTmp);
        String orderColumnName = str[str.length - 1];
        SqlColumn sqlColumn = allColums.get(orderColumnName);
        return " order by " + sqlColumn.getTableName() + SqlConstants.DOT
                + sqlColumn.getTableFieldName() + SqlConstants.SPACE
                + configQuestionModel.getSortRecord().getSortType() + SqlConstants.SPACE;
    }

    /**
     * 生成mysql sql语句
     * 
     * @param PageInfo
     *            pageInfo
     * 
     */
    public void generateTotalExpression(PageInfo pageInfo) {
        StringBuffer limitStringBuffer = new StringBuffer();
        int start = 0;
        int size = -1;

        if (pageInfo == null) {
            return;
        } else {
            if (pageInfo.getCurrentPage() < 0) {
                pageInfo.setCurrentPage(0);
            }
            if (pageInfo.getPageSize() < 0) {
                pageInfo.setPageSize(0);
            }
            start = pageInfo.getCurrentPage() * pageInfo.getPageSize();
            size = pageInfo.getPageSize();
        }
        switch (driver) {
            case "com.mysql.jdbc.Driver": {
                if (start >= 0 && size >= 0) {
                    limitStringBuffer.append(" limit ");
                    limitStringBuffer.append(start);
                    limitStringBuffer.append(SqlConstants.COMMA);
                    limitStringBuffer.append(size);
                    this.setSql(this.getSql() + limitStringBuffer.toString());
                }
                return;
            }
            case "Oracle.jdbc.driver.OracleDriver": {
                int end = -1;
                start = pageInfo.getCurrentPage() * pageInfo.getPageSize();
                end = start + pageInfo.getPageSize();
                if (start >= 0 && end >= 0) {
                    StringBuffer pageString = new StringBuffer();
                    pageString.append("SELECT * FROM (");
                    pageString.append("SELECT A.*, ROWNUM RN");
                    pageString.append(" FROM (SELECT * FROM ( " + this.getSql()
                            + " )) A ");
                    pageString.append(" WHERE ROWNUM <= " + end);
                    pageString.append(") WHERE RN >=  " + start);
                    this.setSql(pageString.toString());
                }
                return;
            }
            default: {
                return;
            }
        }

    }

    /**
     * getSql
     * 
     * @return sql sql
     */
    public String getSql() {
        return sql;
    }

    /**
     * setSql
     * 
     * @param sql
     *            sql
     */
    public void setSql(String sql) {
        this.sql = sql;
    }

    /**
     * getCountSql
     * 
     * @return countSql countSql
     */
    public String getCountSql() {
        return countSql;
    }

    /**
     * countSql
     * 
     * @param countSql
     *            countSql
     */
    public void setCountSql(String countSql) {
        this.countSql = countSql;
    }

    /**
     * getDriver
     * 
     * @return String String
     */
    public String getDriver() {
        return driver;
    }

    /**
     * setDriver
     * 
     * @param driver
     *            driver
     */
    public void setDriver(String driver) {
        this.driver = driver;
    }

    /**
     * @param driver
     *            jdbc driver
     */
    public SqlExpression(String driver) {
        this.driver = driver;
    }
}