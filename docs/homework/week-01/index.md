## 环境检查
java --version

java 17 2021-09-14 LTS

Java(TM) SE Runtime Environment (build 17+35-LTS-2724)

Java HotSpot(TM) 64-Bit Server VM (build 17+35-LTS-2724, mixed mode, sharing)

mvn --version

Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)

Maven home: D:\apache-maven-3.9.11-bin\apache-maven-3.9.11

Java version: 17, vendor: Oracle Corporation, runtime: 

D:\jdk-17

Default locale: zh_CN, platform encoding: GBK

OS name: "windows 10", version: "10.0", arch: "amd64", family: "windows"

git --version

git version 2.47.1.windows.1

之前尝试过Windows装docker，不过本地电脑有点问题，装到vmware虚拟机中

ubuntu@lingdu:~$ docker version

Client:

 Version:           29.1.3

 API version:       1.52

 Go version:        go1.24.4

 Git commit:        29.1.3-0ubuntu3~22.04.2

 Built:             Wed Apr 29 22:18:59 2026

 OS/Arch:           linux/amd64

 Context:           default


Server:

 Engine:

  Version:          29.1.3

  API version:      1.52 (minimum version 1.44)

  Go version:       go1.24.4

  Git commit:       29.1.3-0ubuntu3~22.04.2

  Built:            Wed Apr 29 22:18:59 2026

  OS/Arch:          linux/amd64

  Experimental:     false

 containerd:

  Version:          2.2.1

  GitCommit:        

 runc:

  Version:          1.3.4-0ubuntu1~22.04.1

  GitCommit:        

 docker-init:

  Version:          0.19.0

  GitCommit:        

docker compose version

Docker Compose version v5.5.1

## 概念回答
什么是微服务架构？

微服务架构是一种按业务能力把系统拆成一组小型服务的做法。每个服务只负责一块相对完整的业务，比如订单、支付、库存，能够独立开发、测试、部署和扩展，通常也尽量独立管理自己的数据，服务之间通过 HTTP、gRPC 或消息队列这类轻量方式通信。它并不是单纯把代码拆小，而是希望通过清晰的业务边界和团队分工，让系统更容易演进，但同时也带来网络调用、分布式事务、监控和运维上的新问题。

微服务和单体架构的主要区别是什么？

微服务和单体架构最大的区别，在于系统的组织方式和运行方式不同。单体通常是一个代码库、一个部署单元，模块之间直接函数调用，常常共用一个数据库，扩展时基本是整体一起扩，初期开发简单，但时间久了容易耦合。微服务则是多个独立部署的服务，通过网络通信，各自管自己的数据，可以按需扩展，也能隔离部分故障，但代价是系统更复杂，对自动化部署、监控和团队协作的要求更高。

为什么本课程先实现单体系统，再逐步拆分为微服务？

主要是为了让你先看清业务和领域模型，而不是一上来就被服务发现、网关、配置中心、分布式事务这些基础设施拖住。很多服务边界不是设计出来的，而是在代码和需求变化中慢慢暴露出来的，过早拆分很容易拆成“分布式单体”，看着服务很多，实际还得一起改、一起发。先做单体再演进，既能跑通核心流程，也能让你真正体会拆分的理由和代价。

为什么作业需要提供可重复运行的测试或验证脚本？

是为了让结果不依赖某台电脑、某个人的操作习惯或口头描述。别人拿到代码后，按脚本就能验证接口、输入输出和边界情况，修改代码时也能及时发现旧功能有没有被破坏，评分和反馈会更客观。这其实是在培养一种工程习惯：系统不仅要能跑，还要能用可执行的方式证明它一直能跑。

## 问题记录
无