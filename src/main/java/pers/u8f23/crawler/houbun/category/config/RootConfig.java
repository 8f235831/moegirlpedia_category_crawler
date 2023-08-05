package pers.u8f23.crawler.houbun.category.config;

import lombok.Getter;
import lombok.Setter;

/**
 * @author 8f23
 * @create 2023/8/5-11:34
 */
@Getter @Setter
public class RootConfig
{
	private String outputFilePath;
	private String outputSimplifiedFilePath;
	private String backupFilePath;
	private String rootCateTitle;
	private EmailConfig mailConfig;
}
