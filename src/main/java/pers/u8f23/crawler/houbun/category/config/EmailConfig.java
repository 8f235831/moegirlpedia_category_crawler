package pers.u8f23.crawler.houbun.category.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * @author 8f23
 * @create 2023/8/5-11:34
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailConfig
{
	private String emailHost;
	private String transportType;
	private String fromUser;
	private String fromEmail;
	private String authCode;
	private String mailSubject;
	private List<EmailReceiverConfig> receivers;
}
