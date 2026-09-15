# GitHub Actions 自动打包与发版

本项目使用 `.github/workflows/github_release.yml` 在 GitHub 云端完成 Android 正式版的检查、编译、签名、校验和发布，本地不需要安装 Android Studio 或执行 Gradle 构建。

## 一次性配置

在仓库的 **Settings → Secrets and variables → Actions → New repository secret** 中配置以下四项：

| Secret | 内容 |
| --- | --- |
| `ANDROID_SIGNING_KEY_B64` | JKS/keystore 文件的 Base64 文本 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名密钥别名 |
| `ANDROID_KEY_PASSWORD` | 签名密钥密码 |

密钥文件和密码不得提交到 Git 仓库。签名密钥必须长期备份；一旦丢失，后续版本将无法覆盖安装已经发布的应用。

仓库的 **Settings → Actions → General → Workflow permissions** 可以保持默认的只读权限。工作流只在发布任务中通过 `permissions: contents: write` 获得创建 Release 所需的最小权限。

## 发布正式版

1. 修改 `buildSrc/src/main/kotlin/com/example/util/simpletimetracker/Base.kt` 中的 `versionCode` 和 `versionName`。
2. 将代码提交并推送到 GitHub。
3. 在 GitHub 仓库的 **Releases → Draft a new release → Choose a tag** 中输入与 `versionName` 对应的标签，例如 `v1.59.3`，选择目标分支并创建标签。
4. 推送标签后，工作流自动运行。也可以打开 **Actions → Android signed release → Run workflow**，输入已经存在的标签手动重试。
5. 所有检查通过后，GitHub 自动创建 Release，并上传已签名 APK 和 SHA-256 校验文件。

标签必须使用 `v主版本.次版本.修订号` 格式，并与源码中的 `versionName` 完全一致。带后缀的标签（例如 `v1.60.0-rc.1`）会自动发布为 Pre-release。

如果习惯在本地只管理 Git 标签，也可以在提交版本号后执行：

```text
git tag -a v1.59.3 -m "Release v1.59.3"
git push origin dev
git push origin v1.59.3
```

这些命令只负责上传源码和标签，编译、测试和签名仍全部在 GitHub 云端执行。

## 云端流程

工作流按以下顺序执行：

1. 校验标签格式、标签是否存在，以及标签版本与源码 `versionName` 是否一致。
2. 验证 Gradle Wrapper，配置 Java 21 和 Gradle 缓存。
3. 执行代码风格检查、Debug 单元测试和 Base Release 构建。
4. 对 APK 执行 zipalign，并使用 GitHub Secrets 中的独立证书签名。
5. 验证 APK 签名方案、证书 SHA-256 指纹和文件 SHA-256。
6. 将 APK 保存为 30 天的 Actions Artifact，便于构建失败排查或临时下载。
7. 创建或更新 GitHub Release，上传 APK 与校验文件。

任何一步失败都不会发布 APK。修复问题后，可以在 Actions 页面重新运行失败任务；对于已经存在的 Release，重新运行会安全地替换同名 APK。

## 更新签名证书时

当前工作流会核对公开的证书 SHA-256 指纹。如果有意更换签名密钥，必须同时更新工作流中的 `EXPECTED_CERT_SHA256` 和发布说明里的证书指纹。正常升级不应更换密钥，否则 Android 会把新 APK 视为不同签名，无法覆盖安装旧版本。
