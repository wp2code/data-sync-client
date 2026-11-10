package com.omnikit.ui.programs;

import com.omnikit.core.HttpStaticFileServer;
import com.omnikit.model.ProgramConfig;

/**
 * 程序容器帮助文档：按程序类型给出字段说明、参数说明与完整示例。
 * <p>
 * 由「帮助」按钮调用 {@link #helpText(String)}，在独立的帮助弹窗中展示，便于对照填写配置。
 * 内容与 {@code ProcessContainerService#buildCommand} 的命令组装规则保持一致。
 *
 * @author liuweiping
 * @date 2026-10-30
 **/
public final class ProgramHelpDoc {

    /**
     * 帮助文本的分隔线宽度
     */
    private static final String LINE = "─".repeat(66);

    private ProgramHelpDoc() {
    }

    /**
     * 程序类型的展示名（与程序类型下拉框标签一致）
     */
    public static String typeName(String programType) {
        if (programType == null) {
            return "其他程序";
        }
        return switch (programType) {
            case ProgramConfig.TYPE_JAVA -> "Java 程序";
            case ProgramConfig.TYPE_PYTHON -> "Python 程序";
            case ProgramConfig.TYPE_HTTP_SERVER -> "HTTP 服务";
            default -> "其他程序";
        };
    }

    /**
     * 生成指定程序类型的帮助文本（含字段说明、参数说明与完整示例），以换行结尾便于整段展示
     */
    public static String helpText(String programType) {
        String type = programType == null || programType.isBlank() ? ProgramConfig.TYPE_OTHER : programType;
        String body = switch (type) {
            case ProgramConfig.TYPE_JAVA -> javaHelp();
            case ProgramConfig.TYPE_PYTHON -> pythonHelp();
            case ProgramConfig.TYPE_HTTP_SERVER -> httpHelp();
            default -> otherHelp();
        };
        // HTTP 服务的「参数」就是端口等启动参数，保存在折叠区外；其余类型的参数类字段统一收在折叠区
        String advancedNote = ProgramConfig.TYPE_HTTP_SERVER.equals(type) ? ""
                : "表单折叠区：「JVM 参数」「参数」「环境变量」收在右侧「启动参数与环境变量」标题下，默认收起，\n"
                        + "          点击标题展开或收起；收起时标题左侧显示「已填: …」，提示折叠区内已有配置。\n";
        return LINE + "\n" + "【" + typeName(type) + "】配置帮助\n" + LINE + "\n" + body + LINE + "\n"
                + advancedNote
                + "导入/导出：表单右下角「导出」把选中程序的配置存为 JSON 文件（不含主键与进程号），可拿到其他机器；\n"
                + "          「导入」仅在选择「新增程序」（未选中任何程序）时可用，把文件中的配置载入表单，确认后点「保存」生效，\n"
                + "          文件含多个程序配置时会先选择其中一个，名称与现有程序重复时自动加「-副本」后缀。\n"
                + "提示：在帮助窗口顶部切换「程序类型」，可直接查看其他类型的说明与示例。\n"
                + "帮助窗口：双击或 F11 切换全屏/还原，ESC 退出全屏，右键可复制选中或全部内容。\n";
    }

    /**
     * Java 程序帮助
     */
    private static String javaHelp() {
        return """
               适用场景：Spring Boot jar、普通 main 类等由 java/javaw 启动的程序。

               【字段说明】
                 名称       必填。列表展示名称，全局唯一，不可与其他程序重名（不区分大小写）
                 分组       可选，下拉选择已有分组或直接输入新分组名；留空则不分组，直接平铺展示
                 程序/命令   必填，两种写法任选其一：
                            · 完整命令行：java -jar D:\\app\\order.jar（首词为程序，其余为前置参数）
                            · 单个文件路径：D:\\app\\order.jar（含空格但文件真实存在时按路径处理）
                            含空格的段用双引号包裹，如 java -cp "lib/*;app.jar" com.demo.Main
                 JVM 参数    可选，插在 java 之后、-jar/主类之前，如 -Xms512m -Xmx1024m -Dfile.encoding=UTF-8；
                            程序自带命令行解析（commons-cli 等严格模式）时，用 -Dserver.port=8080 代替
                            --server.port=8080：系统属性同样能被 Spring Boot 读取，且不会出现在 args 里
                 参数       可选，追加在命令行末尾，如 --server.port=8080
                            程序自带的命令行解析不认 --xxx 时，改用「JVM 参数」填 -D 系统属性
                            （JVM 参数框下方的快捷标签就是写 -D 形式）
                 工作目录    可选，子进程工作目录；留空继承应用目录，相对路径以该目录为根解析
                 环境变量    可选，启动时注入子进程：每项 KEY=VALUE，多个用分号分隔；留空则仅继承系统环境
                            如 SERVER_PORT=8080;LOGGING_LEVEL_ROOT=info（Spring Boot 也识别这类环境变量名）
                 JRE/JDK    可选，JDK/JRE 根目录（自动定位 bin/java.exe）或直接指向 java 可执行文件；
                            仅当程序为纯 java/javaw 命令时替换（已带路径的 java 视为自行指定环境）
                 备注       可选，仅用于备注说明

               【常用参数】（对应「JVM 参数」框下方的快捷标签，点击写入参数名后在输入框补全取值）
                 -Dserver.port=8080             服务端口，等价于配置文件中的 server.port
                 -Dspring.profiles.active=prod  指定生效的环境配置（对应 application-prod.yml）
                 -Dlogging.level.root=DEBUG     日志级别：TRACE / DEBUG / INFO / WARN / ERROR，
                                                默认 INFO；也可按包名单独指定日志级别，如
                                                -Dlogging.level.com.demo.dao=DEBUG
                 说明：多个参数用空格分隔；重复点击标签不会重复追加，只会把光标定位到该参数取值处
                 对比：--server.port=8080 写在「参数」框、追加在命令行末尾；-Dserver.port=8080 写在
                       「JVM 参数」框、插在 java 之后。两者 Spring Boot 都能读到，后者不会被 CLI 解析器拒绝

               【完整示例】
                 示例一：指定 JDK 启动 Spring Boot jar
                 名称       订单服务
                 程序类型    Java 程序
                 程序/命令   java -jar D:\\app\\order.jar
                 参数       --server.port=8080 --spring.profiles.active=prod --logging.level.root=INFO
                 工作目录    D:\\app
                 JRE/JDK    D:\\jdk17
                 → 实际执行  D:\\jdk17\\bin\\java.exe -jar D:\\app\\order.jar --server.port=8080 --spring.profiles.active=prod --logging.level.root=INFO
                 → 运行目录  D:\\app

                 示例二：指定主类与类路径（未配置 JRE 时使用系统 PATH 的 java）
                 程序/命令   java -cp "lib/*;app.jar" com.demo.Main
                 参数       --debug
                 → 实际执行  java -cp lib/*;app.jar com.demo.Main --debug

                 示例三：程序自带严格命令行解析（commons-cli 等，不认 --server.port 这类选项）
                 程序/命令   java -jar D:\\app\\auth.jar
                 JVM 参数    -Dserver.port=8908 -Dlogging.level.root=info
                 参数       留空（只填程序自己声明的选项）
                 → 实际执行  java -Dserver.port=8908 -Dlogging.level.root=info -jar D:\\app\\auth.jar
                 → 说明      -D 系统属性不进 args，程序解析器不会报 UnrecognizedOption，Spring Boot 仍然读到配置
                 等价写法   环境变量填 SERVER_PORT=8908;LOGGING_LEVEL_ROOT=info

               """;
    }

    /**
     * Python 程序帮助
     */
    private static String pythonHelp() {
        return """
               适用场景：Python 脚本、或以 python -m 模块方式启动的服务（如 uvicorn、flask）。

               【字段说明】
                 名称        必填。列表展示名称，全局唯一，不可与其他程序重名（不区分大小写）
                 分组        可选，下拉选择已有分组或直接输入新分组名；留空则不分组，直接平铺展示
                 程序/命令    必填，两种写法任选其一：
                             · 入口脚本：D:\\proj\\spider\\main.py（自动前置解释器，脚本本身不可直接执行）
                             · 完整命令行：python -m uvicorn app:app（模块与参数也可写在「参数」中）
                 Python      可选，安装根目录、虚拟环境目录（自动定位 python.exe 或 Scripts/python.exe、bin/python）
                             或直接指向 python 可执行文件；留空使用系统 PATH 的 python。
                             仅对 python/python3 命令与 .py 入口脚本生效
                 参数        可选，追加在命令行末尾，如 --host 0.0.0.0 --port 8000
                 工作目录     可选，子进程工作目录；留空继承应用目录，相对路径（含解释器路径）以该目录为根解析
                 环境变量     可选，启动时注入子进程：每项 KEY=VALUE，多个用分号分隔；留空则仅继承系统环境
                 备注        可选，仅用于备注说明
                 说明：Python 程序启动时自动设置 PYTHONUNBUFFERED=1，控制台实时刷新，无需再加 -u

               【完整示例】
                 示例一：入口脚本 + 虚拟环境
                 名称        数据采集脚本
                 程序类型    Python 程序
                 程序/命令   D:\\proj\\spider\\main.py
                 参数       --task daily --limit 100
                 工作目录    D:\\proj\\spider
                 Python      D:\\proj\\spider\\venv
                 → 实际执行  D:\\proj\\spider\\venv\\Scripts\\python.exe D:\\proj\\spider\\main.py --task daily --limit 100
                 → 运行目录  D:\\proj\\spider

                 示例二：模块方式启动（未配置解释器时使用系统 PATH 的 python）
                 程序/命令   python -m uvicorn app:app
                 参数       --host 0.0.0.0 --port 8000
                 工作目录    D:\\proj\\api
                 → 实际执行  python -m uvicorn app:app --host 0.0.0.0 --port 8000

               """;
    }

    /**
     * 内置 HTTP 静态服务帮助
     */
    private static String httpHelp() {
        return """
               适用场景：把本地目录以静态文件服务方式对外提供（前端 dist 预览、接口联调代理等）。
               服务运行在 OmniKit 进程内，不派生子进程、无 PID，启动后可点「访问」在浏览器打开。

               【字段说明】
                 名称      必填。列表展示名称，全局唯一，不可与其他程序重名（不区分大小写）
                 分组      可选，下拉选择已有分组或直接输入新分组名；留空则不分组，直接平铺展示
                 服务目录   必填，对外提供访问的根目录，必须是已存在的目录；支持 index.html 默认页与目录浏览
                 参数      可选，启动参数见下方参数表，多个用空格分隔
                 备注      可选，仅用于备注说明

               【参数说明】
                 -p / --port 端口         监听端口（1-65535），也可直接写裸端口（如 8080），默认 %d
                 -a / --address 地址       监听与访问地址（如 127.0.0.1、localhost、0.0.0.0）；留空监听全部网卡
                 -P / --proxy 地址         代理后端地址（http:// 或 https:// 开头）：接口请求与本地文件不存在的
                                          请求均转发，携带原始路径、查询串、请求头与请求体
                 --proxy-prefix 前缀       命中该前缀的请求无条件走代理，不被本地同名文件遮蔽；
                                          多个用逗号分隔，如 /api,/admin（需配合 --proxy 使用）
                 --cors[=响应头]           开启跨域（默认 Access-Control-Allow-Origin: *，并本地应答 OPTIONS 预检）；
                                          可传逗号分隔的头列表，每项为「名称」或「名称:值」
                 -S / --tls / --ssl        启用 HTTPS（访问地址变为 https://），--tls=false 可关闭
                 -C / --cert 证书文件       TLS 证书（PEM，默认 cert.pem），相对路径按服务根目录解析
                 -K / --key 私钥文件        TLS 私钥（PEM，默认 key.pem，需未加密的 PKCS#8 或 RSA PKCS#1）
                 写法：--port=8080 与 --port 8080 等价；写了 -C/-K 即视为启用 TLS
                 自签证书：openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 365
                          （浏览器会提示不受信任，确认后继续访问即可）

               【完整示例】
                 示例一：前端静态站点 + 后端接口代理
                 名称       前端站点（本地预览）
                 程序类型   HTTP 服务
                 服务目录   D:\\proj\\web\\dist
                 参数      -p 8080 -P http://127.0.0.1:9000 --proxy-prefix /api --cors
                 → 访问地址  http://localhost:8080/
                 → 实际效果  dist 下存在的文件直接返回；/api/** 与本地不存在的请求转发到 http://127.0.0.1:9000

                 示例二：HTTPS 静态站点
                 服务目录   D:\\www\\static
                 参数      -S -C D:\\cert\\server.pem -K D:\\cert\\server.key
                 → 访问地址  https://localhost:8080/

               """.formatted(HttpStaticFileServer.DEFAULT_PORT);
    }

    /**
     * 其他通用命令帮助
     */
    private static String otherHelp() {
        return """
               适用场景：bat/cmd 脚本、exe 可执行文件或 PATH 中的任意命令（node、nginx 等）。

               【字段说明】
                 名称      必填。列表展示名称，全局唯一，不可与其他程序重名（不区分大小写）
                 分组      可选，下拉选择已有分组或直接输入新分组名；留空则不分组，直接平铺展示
                 程序/命令  必填，两种写法任选其一：
                            · 完整命令行：node app.js（首词为程序，其余为前置参数）
                            · 单个程序/脚本路径：D:\\server\\startup.bat（含空格但文件真实存在时按路径处理）
                            Windows 下 .bat/.cmd 自动以 cmd.exe /c 解释执行；含空格的段用双引号包裹
                 参数      可选，追加在命令行末尾，如 --port=8080
                 工作目录   可选，子进程工作目录；留空继承应用目录，相对路径以该目录为根解析
                 环境变量   可选，启动时注入子进程：每项 KEY=VALUE，多个用分号分隔
                 备注      可选，仅用于备注说明

               【完整示例】
                 示例一：Windows 批处理脚本
                 名称       启动网关
                 程序类型   其他程序
                 程序/命令   D:\\server\\startup.bat
                 参数       --port=8080
                 工作目录    D:\\server
                 → 实际执行  cmd.exe /c D:\\server\\startup.bat --port=8080
                 → 运行目录  D:\\server

                 示例二：PATH 中的命令
                 程序/命令   node
                 参数       D:\\proj\\app.js
                 → 实际执行  node D:\\proj\\app.js

               """;
    }
}
