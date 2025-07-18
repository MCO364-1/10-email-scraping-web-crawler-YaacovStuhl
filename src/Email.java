import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.sql.*;
import java.time.LocalDateTime;

public class Email {
    String email;
    String source;
    long timeStamp;
    String timeStampDate;
    public Email(String email, String source, long timeStamp) {
        this.email = email;
        this.source = source;
        Date date = new Date(timeStamp);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        timeStampDate = sdf.format(date);
    }

}
