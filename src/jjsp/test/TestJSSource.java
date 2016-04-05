package jjsp.container;

public class TestJSSource 
{
    public String getText() {
        return demo();
    }

    public static String demo() {
        return "var cookie = function(url, req, resp, state) {\n" +
                "   var headers = resp.getHeaders();" +
                "   var cookies = [new (Java.type('jjsp.http.Cookie'))('cookie', 'yummy')];" +
                "   headers.setCookies(cookies);" +
                "   resp.configureAsOK();\n" +
                "   resp.sendContent('Check your cookies');\n" +
                "   return true;\n" +
                "};\n" +
                "var js = container.createHandler('cookie');\n" +
                "var pc = container.createHandler('desktop', 'TestHandler', 'Desktop');\n" +
                "var mobile = container.createHandler('mobile', 'TestHandler', 'Mobile');\n" +
                "var tablet = container.createHandler('tablet', 'TestHandler', 'Tablet');\n" +
                "var agent = container.createHandler('agent', 'UserAgentHandler', pc, mobile, tablet);" +
                "var map = {'/cookie':js, '/agent':agent};\n" +
                "var empty = container.createHandler('empty', 'TestHandler', 'empty');\n" +
                "var handler = container.createHandler('root', 'PathMappedURLHandler', '/', map, empty);";
    }

    public static String cookieTest() {
        return "var handler = function(url, req, resp, state) {\n" +
                "   var headers = resp.getHeaders();" +
                "   var cookies = [new (Java.type('jjsp.http.Cookie'))('cookie', 'yummy')];" +
                "   headers.setCookies(cookies);" +
                "   resp.configureAsOK();\n" +
                "   resp.sendContent('Check your cookies');\n" +
                "   return true;\n" +
                "};";
    }

    public static String agent() {
        return  "var pc = container.createHandler('desktop', 'TestHandler', 'Desktop');\n" +
                "var mobile = container.createHandler('mobile', 'TestHandler', 'Mobile');\n" +
                "var tablet = container.createHandler('tablet', 'TestHandler', 'Tablet');\n" +
                "var handler = container.createHandler('root', 'UserAgentHandler', pc, mobile, tablet);";
    }

    public static String json() {
        return "var handler = function(url, ins, outs, state) {\n" +
                "   outs.configureAsOK();\n" +
                "   var json = JSON.parse('{\"key\":\"value\",\"arr\":[\"Hi\"]}');\n" +
                "   outs.sendContent(JSON.stringify(json));\n" +
                "   return true;\n" +
                "};";
    }

    public static String versions() {
        return "    function somefunc(url, ins, outs, state) {\n" +
                "   outs.configureAsOK();\n" +
                "   java.lang.Thread.sleep(50);\n" +
                "   outs.sendContent('Test JS');\n" +
                "   return true;\n" +
                "}\n" +
                "var a = container.createHandler('a', 'TestHandler', 'Alpha');\n" +
                "var a2 = container.createHandler('a2', 'TestHandler', 'Alpha\\'s brother');\n" +
                "var b = container.createHandler('b', 'TestHandler', 'Beta');\n" +
                "var ex = container.createHandler('ex', 'ExceptionalHandler');\n" +
                "var js = container.createHandler('somefunc');\n" +
                "var empty = container.createHandler('empty', 'TestHandler', 'empty');\n" +
                "var sleep = container.createHandler('sleep', 'SleepHandler');\n" +
                "var somedir = container.createHandler('somedir', 'PathMappedURLHandler', '/somedir', {'a2':a2}, empty);" +
                "var map = {'':empty, 'a':a, 'b':b,'sleep':sleep, 'js':js,'somedir':somedir, 'ex':ex};\n" +
                "var handler = container.createHandler('root', 'PathMappedURLHandler', '/', map, empty);";
    }

    public static String jsBenchmark() {
        return "var handler = function(url, ins, outs, state) {\n" +
                "   outs.configureAsOK();\n" +
                "   java.lang.Thread.sleep(50);\n" +
                "   outs.sendContent('Test');\n" +
                "   return true;\n" +
                "};";
    }

    public static String javaBenchmark() {
        return "var handler = container.createHandler('root', 'SleepHandler');";
    }

    public static String jsContainerTest() {
        return "var handler = container.createHandler('root', ['http://localhost/treadstone.jf']);";
    }

    public static String containerTest() {
        return "var handler = container.createHandler('root', 'TestHandler', 'call me maybe..');";
    }
}
