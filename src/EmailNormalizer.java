import java.io.UnsupportedEncodingException;

public class EmailNormalizer {

    public static String normalize(String email) throws UnsupportedEncodingException {
            String newEmail = email.replace("mailto:", "").toLowerCase();
            newEmail = java.net.URLDecoder.decode(newEmail, "UTF-8");
            int queryIdx = newEmail.indexOf('?');
            if (newEmail.contains("?")) {
                newEmail = newEmail.substring(0, queryIdx);//All emails that have a default subject line (i.e. part
                //of the link but not the address have a ? separating the address from the subject line
            }

            return newEmail.trim();//got to account for whitespace
        }
}
