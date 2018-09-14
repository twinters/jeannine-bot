package be.thomaswinters.samsonworld.jeannine;

import be.thomaswinters.twitter.bot.BehaviourCreator;
import be.thomaswinters.twitter.bot.TwitterBot;
import be.thomaswinters.twitter.bot.executor.TwitterBotExecutor;
import be.thomaswinters.twitter.exception.TwitterUnchecker;
import be.thomaswinters.twitter.tweetsfetcher.*;
import be.thomaswinters.twitter.tweetsfetcher.filter.AlreadyParticipatedFilter;
import be.thomaswinters.twitter.tweetsfetcher.filter.AlreadyRepliedToByOthersFilter;
import be.thomaswinters.twitter.userfetcher.ListUserFetcher;
import be.thomaswinters.twitter.util.TwitterLogin;
import be.thomaswinters.util.DataLoader;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JeannineTwitterBot {

    private static final List<String> prohibitedWordsToAnswer = Stream.concat(
            DataLoader.readLinesUnchecked("explicit/bad-words.txt").stream(),
            DataLoader.readLinesUnchecked("explicit/sensitive-topics.txt").stream())
            .collect(Collectors.toList());

    public static void main(String[] args) throws TwitterException, IOException {
        TwitterBot jeannineBot = new JeannineTwitterBot().build();

        new TwitterBotExecutor(jeannineBot).run(args);
    }

    public TwitterBot build() throws IOException {

        long samsonBotsList = 1006565134796500992L;

        Twitter jeannineTwitter = TwitterLogin.getTwitterFromEnvironment("jeannine");

        JeannineTipsGenerator jeannineTipsGenerator = new JeannineTipsGenerator();

        // Bot friends
        Collection<User> botFriends = ListUserFetcher.getUsers(jeannineTwitter, samsonBotsList);
        TweetsFetcherCache botFriendsTweetsFetcher =
                new AdvancedListTweetsFetcher(jeannineTwitter, samsonBotsList, false, true)
//                new ListTweetsFetcher(octaafTwitter, samsonBotsList)
                        .cache(Duration.ofMinutes(5));
        AlreadyRepliedToByOthersFilter alreadyRepliedToByOthersFilter =
                new AlreadyRepliedToByOthersFilter(botFriendsTweetsFetcher);

        ITweetsFetcher tweetsToAnswerJeanine =
                TwitterBot.MENTIONS_RETRIEVER.apply(jeannineTwitter)
                        .combineWith(Arrays.asList(
                                new TimelineTweetsFetcher(jeannineTwitter)
                                        .combineWith(botFriendsTweetsFetcher)
                                        .filter(TwitterUnchecker.uncheck(AlreadyParticipatedFilter::new, jeannineTwitter, 4)),
                                new TweetsFetcherCombiner(
                                        new SearchTweetsFetcher(jeannineTwitter, "jeannine de bolle"),
                                        new SearchTweetsFetcher(jeannineTwitter, "jeanine de bolle"),
                                        new SearchTweetsFetcher(jeannineTwitter, "mevrouw praline")
                                )
                                        .filterRandomly(jeannineTwitter, 1, 4))
                        )
                        // Filter out botfriends tweets randomly
                        .filterRandomlyIf(jeannineTwitter, e -> botFriends.contains(e.getUser()), 1, 15)
                        // Still reply to all octaaf tweets
                        .combineWith(
                                new UserTweetsFetcher(jeannineTwitter, "OctaafBot")
                        )
                        // Filter out own tweets & retweets
                        .filterOutRetweets()
                        // Filter out already replied to messages
//                        .filterRandomlyIf(jeannineTwitter, alreadyRepliedToByOthersFilter, 1, 3)
                        .filterOutOwnTweets(jeannineTwitter)
                        .filterOutMessagesWithWords(prohibitedWordsToAnswer);


        return new TwitterBot(jeannineTwitter,
                BehaviourCreator.empty(),
                BehaviourCreator.fromMessageReactor(jeannineTipsGenerator)
                        .retry(5),
                tweetsToAnswerJeanine);
    }

}
