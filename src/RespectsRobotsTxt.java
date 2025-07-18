import org.apache.commons.io.IOUtils;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RespectsRobotsTxt {
    static ConcurrentHashMap<String, BaseRobotRules> robotsCache = new ConcurrentHashMap<>();//Thread-safe map
    //to ensure that I don't retry failed connections. This was killing me.
    static Logger logger = Logger.getLogger("RespectsRobotsTxt");
    static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    public static boolean respectRobotsTxt(String url) {
        try {
            String root = getRootDomain(url);
            BaseRobotRules rules = robotsCache.computeIfAbsent(root, domain -> {
                BaseRobotRules fetched = fetchAndParseRobots(domain);
                if (fetched == null) {
                    logger.warning("Failed to get robots.txt from: " + domain);
                    return new SimpleRobotRules(SimpleRobotRules.RobotRulesMode.ALLOW_ALL);//If there is no robots.txt not because of any
                    //connection failure, then it would seem to be fine.
                }
                return fetched;
            });

            return rules.isAllowed(url);

        } catch (Exception e) {
            logger.warning("Error processing robots.txt for: " + url + " — " + e.getMessage());
            return true; // Again if it fails, or has an error, I'm going to assume its okay
        }
    }

    private static BaseRobotRules fetchAndParseRobots(String rootUrl) {
        String robotsTxtUrl = rootUrl + "/robots.txt";
            try {
                Document connection = Jsoup.connect(rootUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(20000)  // 5 second timeout
                        .get();
                byte[] content = IOUtils.toByteArray(connection.text());//Apparently the parser reads bytes and not strings or inputsStream.
                // Good to know.
                SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
                System.out.println("robots.txt parsed: " + robotsTxtUrl);
                return parser.parseContent(robotsTxtUrl, content, "text/plain", USER_AGENT);//This is so much simpler than what
                //I was doing before. I was reading all of the contents into a HashSet and then running .contains() with the .startswith() method

        } catch (Exception e) {
            logger.warning("Failed to get robots.txt from: " + rootUrl);
            return null;
        }
    }

    private static String getRootDomain(String url) throws Exception {//Method to find the actual root of a website domain
        //to then add on robots.txt to it and then find said robots.txt page for that domain
        URL u = new URL(url);
        String protocol = u.getProtocol();
        String host = u.getHost();
        return protocol + "://" + host;
    }
}
