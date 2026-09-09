package scheduling.scheduling.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import scheduling.scheduling.entity.Account;

@Service
public class GmailAccountMailService implements AccountMailService {

    private static final Logger log = LoggerFactory.getLogger(GmailAccountMailService.class);

    private final JavaMailSender mailSender;
    private final String username;
    private final String appPassword;
    private final String from;

    public GmailAccountMailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String appPassword,
            @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.username = username;
        this.appPassword = appPassword;
        this.from = from;
    }

    @Override
    public void sendActivationOtp(Account account, String rawOtp) {
        String recipient = account.getEmployee() == null ? null : account.getEmployee().getEmail();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(appPassword)) {
            log.info("Mail credentials are not configured; skipping activation OTP email for account {}", account.getUsername());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(StringUtils.hasText(from) ? from : username);
            message.setTo(recipient);
            message.setSubject("Account activation OTP");
            message.setText("Your activation OTP is: " + rawOtp);
            mailSender.send(message);
        }
        catch (MailException ex) {
            throw new MailDeliveryException("Failed to send activation OTP email", ex);
        }
    }
}
