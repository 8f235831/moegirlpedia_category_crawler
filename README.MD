## 萌摆备份程序

适用于芳文专题条目的自动化备份程序。源程序使用模块化设计，因此可以改造后用于备份其它条目。

程序功能同时依赖于萌百本站的页面服务与其镜像站（[moegirl.uk](https://moegirl.uk )）的页面导出服务。

导出数据应在其授权协议下使用，超出范围使用所造成的后果应由使用者自行承担。

### 使用

执行`gradlew build`，编译得到可执行程序的压缩包。在合适的目录解包后，放置配置文件，随后即可执行。

根据所在操作系统的不同，执行的启动命令有所不同。

* Windows: `.\bin\moegirlpedia-houbun-category.bat [config_path]`
* Linux: `sh ./bin/moegirlpedia-houbun-category [config_path]`

推荐配置为定期任务。

### 配置文件

#### 路径

默认配置文件为相对路径下的`config.json`。如果有需要，也可以启动程序时通过参数指定。

#### 配置格式

示例配置：

```json
{
  "outputFilePath": "/hdd/mgp-houbun-backup/backup_%s.xml",
  "compressedFilePath": "/hdd/mgp-houbun-backup/backup_%s.7z",
  "exceptionTraceFilePath": "/hdd/mgp-houbun-backup/trace_%s.txt",
  "retryTimes": 3,
  "bufferSize": 2048,
  "emailConfig": {
    "emailHost": "example.com",
    "transportType": "smtp",
    "fromUser": "mgp-backup@example.com",
    "fromEmail": "mgp-backup@example.com",
    "authCode": "********",
    "mailSubject": "[MGP芳文组]自动备份任务报告",
    "receivers": [
      {
        "address": "receiver@example.com",
        "mailSubject": "[MGP芳文组]自动备份任务报告",
        "sendAttachment": true,
        "sendErrorStackTrace": true
      }
    ]
  }
}
```

字段说明如下：

```typescript
{
    // 原始备份文件的输出路径。其中如果包含了"%s"，则会替换为时间戳。
    outputFilePath: string;
    // 备份压缩文件的输出路径。其中如果包含了"%s"，则会替换为时间戳。
    compressedFilePath: string;
    // 错误文件的输出路径。其中如果包含了"%s"，则会替换为时间戳。
    exceptionTraceFilePath: string;
    // 请求失败重试次数。
    retryTimes: number;
    // 缓冲区大小，推荐2-4K。
    bufferSize: number;
    // 邮件配置。
    emailConfig: {
        // 邮件服务器地址。
        emailHost: string;
        // 邮件协议名，如"smtp".
        transportType: string;
        // 发送人。
        fromUser: string;
        // 发送自邮箱。
        fromEmail: string;
        // 邮箱访问口令。
        authCode: string;
        // 邮件主题。
        mailSubject: string;
        // 接收人配置。
        receivers: Array<{
            // 地址。
            address: string;
            // 邮件主题。可选，如果指定则会覆盖上层的主题配置。
            mailSubject?: string;
            // 是否将结果作为附件发送。发送的文件是压缩过的（否则太大发不出去）。
            sendAttachment: boolean;
            // 是否在出现错误时将错误报告作为附件发送。
            sendErrorStackTrace: boolean;
        }>;
    }
}
```

### 坑

* 对不同访问过程的代理功能支持；

### 更新记录

#### 1.1

* 分离页面收集与备份的过程；
* 新增备份文件压缩的功能；
* 新增通过邮件发送备份文件的功能；

### 致谢

感谢众多编辑对条目内容所作出的贡献，感谢镜像站提供的页面导出服务，以及感谢下列第三方库的开发者们为我节省了造轮子的时间。

* [Project Lombok](https://projectlombok.org/)
* [SLF4J](https://www.slf4j.org/)
* [RxJava3](https://github.com/ReactiveX/RxJava)
* [Gson](https://github.com/google/gson)
* [Retrofit2](https://square.github.io/retrofit/)
* [OkHttp3](https://square.github.io/okhttp/)
* [jsoup](https://jsoup.org/)
* [Jakarta Mail](https://jakartaee.github.io/mail-api/)
* [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/)
* [XZ Utils](https://tukaani.org/xz/)