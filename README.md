# WMS Inbound

WMS（仓库管理系统）入库管理后端服务

## 技术栈

- Spring Boot 4.0.8
- Java 17
- MyBatis 4.0.1
- MySQL 8
- Redis
- Druid 数据库连接池
- JWT 认证
- Knife4j API 文档

## 开发环境

### 前置要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 数据库配置

1. 创建数据库：
```sql
CREATE DATABASE wms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 配置数据库连接：编辑 `src/main/resources/application.yaml`

### 启动服务

```bash
# 使用 Maven Wrapper
./mvnw spring-boot:run

# 或打包后运行
./mvnw clean package
java -jar target/wms-practice-0.0.1-SNAPSHOT.jar
```

访问 http://localhost:8080

### API 文档

启动服务后访问：http://localhost:8080/doc.html

## 前端项目

前端项目已独立分离至：`../wms-inbound-web`

开发时需同时启动前后端：
```bash
# 终端 1: 启动后端
cd wms-inbound
./mvnw spring-boot:run

# 终端 2: 启动前端
cd wms-inbound-web
npm run dev
```

## 项目结构

```
src/main/java/com/example/wms/
├── config/           # 配置类
├── controller/       # 控制器
├── dto/              # 数据传输对象
├── entity/           # 实体类
├── exception/        # 异常处理
├── mapper/           # MyBatis Mapper
├── service/          # 业务逻辑
└── utils/            # 工具类
```

## CORS 配置

后端已配置 CORS 允许 `http://localhost:5173`（前端开发服务器）访问。

如需修改，编辑 `src/main/java/com/example/wms/config/CorsConfig.java`

## 部署

### 前后端一体部署

如需将前端打包到后端：

```bash
# 1. 构建前端
cd ../wms-inbound-web
npm run build

# 2. 复制到后端 static 目录
cp -r dist/* ../wms-inbound/src/main/resources/static/

# 3. 打包后端
cd ../wms-inbound
./mvnw clean package

# 4. 运行
java -jar target/wms-practice-0.0.1-SNAPSHOT.jar
```

### 前后端分离部署

- 后端：打包成 jar 部署到服务器
- 前端：构建后部署到 Nginx 或 CDN
- 注意更新 CORS 配置的 `allowedOrigins`
