define(["template"],function(a){function b(b){"use strict";b=b||{};var c=a.utils,d=(c.$helpers,c.$each),e=b.indList,f=(b.$ind,b.name,c.$escape),g="";return g+="<!--\r\n数据例子：\r\nvar demoData = {\r\n    indList: {\r\n        click: {\r\n            caption: '',\r\n            color: ''\r\n        }\r\n    }\r\n};\r\n-->\r\n<!-- 指标颜色设置 -->\r\n<div class=\"ind-color\">\r\n    <div class=\"ind-color-alone\">\r\n        <span>对各指标颜色进行单独设置</span>\r\n        ",d(e,function(a,b){g+='\r\n        <div class="ind-color-alone-ind">\r\n            <span>',g+=f(a.caption),g+='：</span>\r\n            <input type="text" name="',g+=f(b),g+='" value="',g+=f(a.color),g+='" />\r\n        </div>\r\n        '}),g+="\r\n    </div>\r\n</div>\r\n"}return{render:b}});