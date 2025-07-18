import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailScraper {
    public static void main(String[] args) throws Exception, SQLException {

        ExecutorService executor = Executors.newFixedThreadPool(60);
        Set<String> linksVisited = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Set<String> linksFound = Collections.newSetFromMap(new ConcurrentHashMap<>());
        Queue<String> linkQueue = new ConcurrentLinkedQueue<>();
        linksFound.add("https://www.touro.edu/");
        linkQueue.add("https://www.touro.edu/");
        ConcurrentHashMap<String,Email> emailsFound = new ConcurrentHashMap<>(10000);//I'm hoping this will reduce resizing costs
        Logger logger = Logger.getLogger("EmailScraper");
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger emailCount = new AtomicInteger();//thread-safe integer to track size of found emails
        AtomicInteger idleThreads = new AtomicInteger(0);
        boolean[] shutdownFlag = { false };


        for (int i = 0; i < 40; i++) {
            futures.add(executor.submit(() -> {

                while (emailCount.get() < 10000) {
                    String startingURL = linkQueue.poll();
                    if (startingURL == null) {
                        int currentIdle = idleThreads.incrementAndGet();

                        try {//I put this here A) Because the other 59 threads start off with a null startingUrl B) My program was stalling and I thought it might be because of idle threads
                            Thread.sleep(200);
                        } catch (Exception e) {
                            Thread.currentThread().interrupt();
                            break;
                        }

                        idleThreads.decrementAndGet();

                        if (linkQueue.isEmpty() && currentIdle >= 40) {
                            shutdownFlag[0] = true;
                            break;
                        }

                        continue;
                    }
                    if(!RespectsRobotsTxt.respectRobotsTxt(startingURL)) {
                        linksVisited.add(startingURL);
                        continue;}//A method in a class i created to see if a path is allowed by robots.txt
                    //and skips it if its not allowed
                    if (linksVisited.contains(startingURL)) {
                        continue;
                    }


                    try {
                        Document doc = Jsoup.connect(startingURL)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                .timeout(20000)
                                .get();

                        linksVisited.add(startingURL);


                        Elements links = doc.getElementsByTag("a");
                        Pattern urlPattern = Pattern.compile("https?://[^\\s]+?\\.(com|edu|net|gov|co|org)(?:[/\\?#]|$)", Pattern.CASE_INSENSITIVE);
                        Pattern notLinkedIn = Pattern.compile(".*linkedin.*", Pattern.CASE_INSENSITIVE);//I was getting really tired of
                        //all the failed attempts to scrape linkedin pages so I decided to filter them out. Along with all the other ones
                        Pattern xPattern = Pattern.compile("https://x.com");
                        Pattern twitterPattern = Pattern.compile("https://twitter.com");
                        Pattern gitHubPattern = Pattern.compile(".*github.com*.*", Pattern.CASE_INSENSITIVE);
                        Pattern googleSorryPattern = Pattern.compile(".*google.com/sorry.*", Pattern.CASE_INSENSITIVE);
                        Pattern facebookPattern = Pattern.compile(".*facebook.com.*", Pattern.CASE_INSENSITIVE);

                            for (Element link : links) {
                                String url = link.absUrl("href");
                                Matcher matcher = urlPattern.matcher(url);


                                if (matcher.find() && !url.isEmpty() && !linksVisited.contains(url)
                                ) {
                                    if (!linksFound.contains(url)) {
                                        linksFound.add(url);
                                        linkQueue.add(url);
                                    }
                                }
                            }
                        Matcher notLinkedInMatcher = notLinkedIn.matcher(startingURL);
                        Matcher notTwitterMatcher = twitterPattern.matcher(startingURL);
                        Matcher notXMatcher = xPattern.matcher(startingURL);
                        Matcher notGitHubMatcher = gitHubPattern.matcher(startingURL);
                        Matcher googleSorryMatcher = googleSorryPattern.matcher(startingURL);
                        Matcher facebookMatcher = facebookPattern.matcher(startingURL);

                        if(!notLinkedInMatcher.find()//A compendium of all the websites I got consistently blocked from. I did at some point have
                                //an if-else statement that funneled those requests to a headless browser but I felt like it would slow everything down because
                                //those take longer, so I opted to just skip it instead
                                && !notTwitterMatcher.find()
                                && !notXMatcher.find()
                                && !notXMatcher.find()
                                && !notGitHubMatcher.find()
                                && !googleSorryMatcher.find()
                                && !facebookMatcher.find()) {
                            Elements emailLinks = doc.getElementsByAttributeValueContaining("href", "mailto:");
                            for (Element email : emailLinks) {
                                Pattern patternForEmails = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
                                Matcher matcherForEmails = patternForEmails.matcher(email.attr("href").replace("mailto:", ""));
                                if (matcherForEmails.find()) {
                                    String normalizedEmail = EmailNormalizer.normalize(email.attr("href"));//Normalized email is a class to format the email into a storable string
                                    if (emailCount.get() >= 10000) return;
                                    if (emailsFound.putIfAbsent(normalizedEmail, new Email(normalizedEmail, startingURL, System.currentTimeMillis())) == null) {
                                        int count = emailCount.incrementAndGet();
                                        System.out.println("email submitted: " + emailsFound.get(normalizedEmail).email + " source: " + emailsFound.get(normalizedEmail).source + " at " + emailsFound.get(normalizedEmail).timeStampDate);
                                        System.out.println("Total emails: " + emailsFound.size());
                                    }//I had code to do the javascript or other pages that were blocked but when I ran it,
                                    //it was often slower than just ignoring the links altogether.
                                }
                            }
                        }
                    }catch(Exception e){
                        logger.warning(e.getMessage());
                        linksVisited.add(startingURL);
                    }
                }
            }));
        }

        executor.shutdown();

        while (!executor.isTerminated()) {//I wanted to get 10000 emails exactly. Just another failsafe because I had issues before
            if (emailCount.get() >= 10000 || shutdownFlag[0]) {
                executor.shutdownNow();
                break;
            }
            Thread.sleep(100);
        }

        String endpoint = "database-1.ckxf3a0k0vuw.us-east-1.rds.amazonaws.com";

        String connectionUrl =
                "jdbc:sqlserver://" + endpoint + ";"
                        + "database=YaacovStuhl;"
                        + "user=MCON364;"
                        + "password=Pesach2025;"
                        + "encrypt=true;"
                        + "trustServerCertificate=true;"
                        + "loginTimeout=30;";

        try (Connection connection = DriverManager.getConnection(connectionUrl)) {
            String insertSql = "INSERT INTO emails (EmailAddress,Source,TimeStamp) VALUES (?, ?, ?);";
            connection.setAutoCommit(false);

            try (PreparedStatement preparedStatement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                AtomicInteger batchSize = new AtomicInteger();
                final int batchLimit = 100;
                emailsFound.forEach((key, value) -> {
                    try {
                        preparedStatement.setString(1, value.email);
                        preparedStatement.setString(2, value.source);
                        preparedStatement.setString(3, value.timeStampDate);//I converted the timeStamp to a readable string in my Email
                        preparedStatement.addBatch();
                        batchSize.getAndIncrement();

                        if(batchSize.get() % batchLimit == 0) {
                            preparedStatement.executeBatch();
                            connection.commit();
                        }
                    } catch (SQLException e) {
                        logger.warning("Insertion error: " + e.getMessage());
                    }
                });
            }catch (Exception e){
            logger.warning(e.getMessage());}
        }

    }
}

