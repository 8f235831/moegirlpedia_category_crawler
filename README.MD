### 萌摆备份程序
#### 配置文件
##### 路径
默认配置文件为相对路径下的`config.json`。如果有需要，也可以启动程序时通过参数指定。
##### 配置格式
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