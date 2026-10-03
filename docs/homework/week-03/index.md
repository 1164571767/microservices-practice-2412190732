本周把项目骨架搭好，依赖引入，计划下周将全局异常处理，统一数据类型等通用型抽象出来
启动命令./mvnw spring-boot:run
接口响应：GET /api/system/status → 200 {"application":"monolith","status":"UP","version":"0.0.1-SNAPSHOT"}；/actuator/health → 200
{"groups":["liveness","readiness"],"status":"UP"}
测试结果：[INFO]
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.zjgsu.sfy.ArchitectureTest
18:34:55.404 [main] INFO com.tngtech.archunit.core.PluginLoader -- Detected Java version 25
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.235 s -- in com.zjgsu.sfy.ArchitectureTest
[INFO] Running com.zjgsu.sfy.ModularityTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.157 s -- in com.zjgsu.sfy.ModularityTests
[INFO] Running com.zjgsu.sfy.MonolithApplicationTests
18:34:57.797 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.zjgsu.sfy.MonolithApplicationTests]: MonolithApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.     
18:34:57.897 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.MonolithApplicationTests
18:34:57.938 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.zjgsu.sfy.MonolithApplicationTests]: MonolithApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.     
18:34:57.940 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.MonolithApplicationTests

.   ____          _            __ _ _
/\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
\\/  ___)| |_)| | | | | || (_| |  ) ) ) )
'  |____| .__|_| |_|_| |_\__, | / / / /
=========|_|==============|___/=/_/_/_/

:: Spring Boot ::                (v4.0.8)

2026-10-03T18:34:58.277+08:00  INFO 15340 --- [monolith] [           main] com.zjgsu.sfy.MonolithApplicationTests   : Starting MonolithApplicationTests using Java 25 with PID 15340 (started by SFY11 in D:\microservices-practice-2412190732\monolith)
2026-10-03T18:34:58.279+08:00  INFO 15340 --- [monolith] [           main] com.zjgsu.sfy.MonolithApplicationTests   : No active profile set, falling back to 1 default profile: "default"
2026-10-03T18:35:00.727+08:00  INFO 15340 --- [monolith] [           main] o.s.b.a.e.web.EndpointLinksResolver      : Exposing 2 endpoints beneath base path '/actuator'
2026-10-03T18:35:00.799+08:00  INFO 15340 --- [monolith] [           main] o.s.s.config.TaskSchedulerRouter         : No TaskScheduler/ScheduledExecutorService bean found for scheduled processing
2026-10-03T18:35:00.805+08:00  INFO 15340 --- [monolith] [           main] com.zjgsu.sfy.MonolithApplicationTests   : Started MonolithApplicationTests in 2.773 seconds (process running for 7.084)
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
WARNING: A Java agent has been loaded dynamically (C:\Users\SFY11\.m2\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
Java HotSpot(TM) 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.939 s -- in com.zjgsu.sfy.MonolithApplicationTests
[INFO] Running com.zjgsu.sfy.system.web.SystemStatusControllerTest
2026-10-03T18:35:01.695+08:00  INFO 15340 --- [monolith] [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for
test class [com.zjgsu.sfy.system.web.SystemStatusControllerTest]: SystemStatusControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-10-03T18:35:01.728+08:00  INFO 15340 --- [monolith] [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.system.web.SystemStatusControllerTest
2026-10-03T18:35:01.729+08:00  INFO 15340 --- [monolith] [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration classes for
test class [com.zjgsu.sfy.system.web.SystemStatusControllerTest]: SystemStatusControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-10-03T18:35:01.736+08:00  INFO 15340 --- [monolith] [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.system.web.SystemStatusControllerTest

.   ____          _            __ _ _
/\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
\\/  ___)| |_)| | | | | || (_| |  ) ) ) )
'  |____| .__|_| |_|_| |_\__, | / / / /
=========|_|==============|___/=/_/_/_/

:: Spring Boot ::                (v4.0.8)

2026-10-03T18:35:01.773+08:00  INFO 15340 --- [monolith] [           main] c.z.s.s.web.SystemStatusControllerTest   : Starting SystemStatusControllerTest using Java 25 with PID 15340 (started by SFY11 in D:\microservices-practice-2412190732\monolith)
2026-10-03T18:35:01.773+08:00  INFO 15340 --- [monolith] [           main] c.z.s.s.web.SystemStatusControllerTest   : No active profile set, falling back to 1 default profile: "default"
2026-10-03T18:35:01.935+08:00  INFO 15340 --- [monolith] [           main] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-10-03T18:35:01.935+08:00  INFO 15340 --- [monolith] [           main] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-10-03T18:35:01.936+08:00  INFO 15340 --- [monolith] [           main] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 1 ms
2026-10-03T18:35:01.947+08:00  INFO 15340 --- [monolith] [           main] c.z.s.s.web.SystemStatusControllerTest   : Started SystemStatusControllerTest in 0.204s (process running for 8.225)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.407 s -- in com.zjgsu.sfy.system.web.SystemStatusControllerTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  02:07 min
[INFO] Finished at: 2026-10-03T18:35:02+08:00
[INFO] ------------------------------------------------------------------------
PS D:\microservices-practice-2412190732\monolith>  ./mvnw test
[INFO] Scanning for projects...
[INFO]
[INFO] -----------------------< com.zjgsu.sfy:monolith >-----------------------
[INFO] Building monolith 0.0.1-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- enforcer:3.5.0:enforce (enforce) @ monolith ---
[INFO] Rule 0: org.apache.maven.enforcer.rules.version.RequireJavaVersion passed
[INFO] Rule 1: org.apache.maven.enforcer.rules.version.RequireMavenVersion passed
[INFO]
[INFO] --- jacoco:0.8.13:prepare-agent (prepare-agent) @ monolith ---
[INFO] argLine set to -javaagent:C:\\Users\\SFY11\\.m2\\repository\\org\\jacoco\\org.jacoco.agent\\0.8.13\\org.jacoco.agent-0.8.13-runtime.jar=destfile=D:\\microservices-practice-2412190732\\monolith\\target\\jacoco.exec
[INFO]
[INFO] --- resources:3.3.1:resources (default-resources) @ monolith ---
[INFO] Copying 1 resource from src\main\resources to target\classes
[INFO] Copying 0 resource from src\main\resources to target\classes
[INFO]
[INFO] --- compiler:3.14.1:compile (default-compile) @ monolith ---
[INFO] Nothing to compile - all classes are up to date.
[INFO]
[INFO] --- resources:3.3.1:testResources (default-testResources) @ monolith ---
[INFO] skip non existing resourceDirectory D:\microservices-practice-2412190732\monolith\src\test\resources
[INFO]
[INFO] --- compiler:3.14.1:testCompile (default-testCompile) @ monolith ---
[INFO] Nothing to compile - all classes are up to date.
[INFO]
[INFO] --- surefire:3.5.6:test (default-test) @ monolith ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO]
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.zjgsu.sfy.ArchitectureTest
20:43:26.587 [main] INFO com.tngtech.archunit.core.PluginLoader -- Detected Java version 25
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.309 s -- in com.zjgsu.sfy.ArchitectureTest
[INFO] Running com.zjgsu.sfy.ModularityTests
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.208 s -- in com.zjgsu.sfy.ModularityTests
[INFO] Running com.zjgsu.sfy.MonolithApplicationTests
20:43:29.108 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test c
lass [com.zjgsu.sfy.MonolithApplicationTests]: MonolithApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
20:43:29.188 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.MonolithApplicationTests
20:43:29.215 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test c
lass [com.zjgsu.sfy.MonolithApplicationTests]: MonolithApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
20:43:29.217 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.MonolithApplicationTests

.   ____          _            __ _ _
/\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
\\/  ___)| |_)| | | | | || (_| |  ) ) ) )
'  |____| .__|_| |_|_| |_\__, | / / / /
=========|_|==============|___/=/_/_/_/

:: Spring Boot ::                (v4.0.8)

2026-10-03T20:43:29.507+08:00  INFO 25988 --- [monolith] [           main] com.zjgsu.sfy.MonolithApplicationTests   : Starting MonolithApplicationTests using Java 25 with PID 25988 (started by SFY11 in D:\microservices-practice-2412190732\monolith)
2026-10-03T20:43:29.509+08:00  INFO 25988 --- [monolith] [           main] com.zjgsu.sfy.MonolithApplicationTests   : No active profile set, falling back to 1 default profile: "default"
2026-10-03T20:43:31.537+08:00  INFO 25988 --- [monolith] [           main] o.s.b.a.e.web.EndpointLinksResolver      : Exposing 2 endpoints beneath base path '/actuator'
2026-10-03T20:43:31.605+08:00  INFO 25988 --- [monolith] [           main] o.s.s.config.TaskSchedulerRouter         : No TaskScheduler/ScheduledExecutorService bean found for scheduled processing
2026-10-03T20:43:31.613+08:00  INFO 25988 --- [monolith] [           main] com.zjgsu.sfy.MonolithApplicationTests   : Started MonolithApplicationTests in 2.314 seconds (process running for 6.326)
Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work in future releases of the JDK. Please add Mockito as an agent to your build as described in Mockito's documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
WARNING: A Java agent has been loaded dynamically (C:\Users\SFY11\.m2\repository\net\bytebuddy\byte-buddy-agent\1.17.8\byte-buddy-agent-1.17.8.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
Java HotSpot(TM) 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.376 s -- in com.zjgsu.sfy.MonolithApplicationTests
[INFO] Running com.zjgsu.sfy.system.web.SystemStatusControllerTest
2026-10-03T20:43:32.444+08:00  INFO 25988 --- [monolith] [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration clas
ses for test class [com.zjgsu.sfy.system.web.SystemStatusControllerTest]: SystemStatusControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-10-03T20:43:32.477+08:00  INFO 25988 --- [monolith] [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.system.web.SystemStatusControllerTest
2026-10-03T20:43:32.480+08:00  INFO 25988 --- [monolith] [           main] t.c.s.AnnotationConfigContextLoaderUtils : Could not detect default configuration clas
ses for test class [com.zjgsu.sfy.system.web.SystemStatusControllerTest]: SystemStatusControllerTest does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
2026-10-03T20:43:32.487+08:00  INFO 25988 --- [monolith] [           main] .b.t.c.SpringBootTestContextBootstrapper : Found @SpringBootConfiguration com.zjgsu.sfy.MonolithApplication for test class com.zjgsu.sfy.system.web.SystemStatusControllerTest

.   ____          _            __ _ _
/\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
\\/  ___)| |_)| | | | | || (_| |  ) ) ) )
'  |____| .__|_| |_|_| |_\__, | / / / /
=========|_|==============|___/=/_/_/_/

:: Spring Boot ::                (v4.0.8)

2026-10-03T20:43:32.528+08:00  INFO 25988 --- [monolith] [           main] c.z.s.s.web.SystemStatusControllerTest   : Starting SystemStatusControllerTest using Java 25 with PID 25988 (started by SFY11 in D:\microservices-practice-2412190732\monolith)
2026-10-03T20:43:32.528+08:00  INFO 25988 --- [monolith] [           main] c.z.s.s.web.SystemStatusControllerTest   : No active profile set, falling back to 1 default profile: "default"
2026-10-03T20:43:32.708+08:00  INFO 25988 --- [monolith] [           main] o.s.b.t.m.w.SpringBootMockServletContext : Initializing Spring TestDispatcherServlet ''
2026-10-03T20:43:32.708+08:00  INFO 25988 --- [monolith] [           main] o.s.t.web.servlet.TestDispatcherServlet  : Initializing Servlet ''
2026-10-03T20:43:32.710+08:00  INFO 25988 --- [monolith] [           main] o.s.t.web.servlet.TestDispatcherServlet  : Completed initialization in 1 ms
2026-10-03T20:43:32.722+08:00  INFO 25988 --- [monolith] [           main] c.z.s.s.web.SystemStatusControllerTest   : Started SystemStatusControllerTest in 0.225 seconds (process running for 7.435)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.452 s -- in com.zjgsu.sfy.system.web.SystemStatusControllerTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.136 s
[INFO] Finished at: 2026-10-03T20:43:33+08:00
[INFO] ------------------------------------------------------------------------
