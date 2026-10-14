# SC6019 第六个 Topic：仓库使用说明

本仓库用于 SC6019 Option 6（跨 Rollup 意图路由器）。
已有 capstone 的 Go CLI、Python agent、Java 服务、浏览器界面、测试和文档作为基础保留。
课程选题与新增范围见 [SC6019_TOPIC6.md](SC6019_TOPIC6.md)。
路由器、路由比较界面与实验尚待实现。
来源见 [ORIGINAL_SOURCE.md](ORIGINAL_SOURCE.md)。新 Git 历史以一次导入提交开始。

## 提交到自己的 GitHub 仓库

1. 在目标 GitHub 账号中新建一个空仓库，例如 `sc6019-cross-rollup-intent-router`。仓库公开或私有由你选择。
2. 创建时不要自动添加 README、LICENSE 或 `.gitignore`，本地项目已有文件和提交。
3. 在本目录运行下面的命令，把示例地址换成你实际创建的仓库地址：

```bash
git remote add origin https://github.com/liaojianhan123-cyber/sc6019-cross-rollup-intent-router.git
git push -u origin main
```

推送时使用你自己的 GitHub 身份登录。项目目录中没有保存访问令牌或 GitHub 密码。
当前本地提交身份为 `liaojianhan123-cyber`，使用该账号的 GitHub noreply 邮箱。
以后如果换账号，请先更新本仓库的 `git config user.name` 和 `git config user.email`。

查看当前提交与仓库连接：

```bash
git log --oneline
git remote -v
```

## 构建完整项目

从项目根目录构建 Go CLI，并记录二进制绝对路径：

```bash
go build -o defi ./cmd/defi
export DEFI_CLI_PATH="$(pwd)/defi"
./defi version --long
```

Java 服务需要 Java 17、Maven 和 Docker Compose 所提供的 MySQL、Redis、Kafka：

```bash
cd defi-agent-service
docker compose up -d
mvn test
mvn spring-boot:run
```

浏览器打开 `http://localhost:8080`。其他配置见
[Java 服务 README](defi-agent-service/README.md)。
没有配置模型 API 密钥时，聊天使用规则模式；聊天回复“确认”走 paper 模拟执行。
钱包执行路径会请求用户签名。模拟结果和真实交易须按服务文档区分。

Python agent 源码在 [agent/](agent/) 中。可从项目根目录安装依赖并查看参数：

```bash
python -m pip install -r agent/requirements.txt
python agent/defi_agent.py --help
```

实际模型调用需要配置 `ANTHROPIC_API_KEY`；配置项见 `agent/config.py`。

## 后续开发

- 初始导入没有改动运行代码，不代表新增或重新验证了交易能力。
- `.env`、私钥、日志、编译产物和本地数据库文件不应加入提交。
- Go 模块路径暂时保留原值；更换 GitHub 仓库不要求立即重写内部 import。
- 原 README 中的安装链接、`go install` 地址及文档站配置仍指向原项目。
  开发这份独立副本时，请先按上面的方式从本地源代码构建。
- 原 GitHub Actions 工作流已保留；新仓库的 CI、发布及文档站需要在新仓库中验证。

课程开发优先实现意图格式、双环境路由、路由解释和基准实验。
长期记忆、向量检索和新增钱包集成可以作为后续扩展。
