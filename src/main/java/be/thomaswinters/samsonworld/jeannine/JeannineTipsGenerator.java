package be.thomaswinters.samsonworld.jeannine;

import be.thomaswinters.action.ActionExtractor;
import be.thomaswinters.action.data.ActionDescription;
import be.thomaswinters.chatbot.IChatBot;
import be.thomaswinters.chatbot.data.IChatMessage;
import be.thomaswinters.generator.fitness.IFitnessFunction;
import be.thomaswinters.generator.selection.ISelector;
import be.thomaswinters.generator.selection.TournamentSelection;
import be.thomaswinters.language.dutch.DutchFirstPersonConverter;
import be.thomaswinters.random.Picker;
import be.thomaswinters.replacement.Replacer;
import be.thomaswinters.replacement.Replacers;
import be.thomaswinters.sentence.SentenceUtil;
import be.thomaswinters.textgeneration.domain.context.TextGeneratorContext;
import be.thomaswinters.textgeneration.domain.factories.command.CommandFactory;
import be.thomaswinters.textgeneration.domain.factories.command.SingleTextGeneratorArgumentCommandFactory;
import be.thomaswinters.textgeneration.domain.generators.commands.LambdaSingleGeneratorArgumentCommand;
import be.thomaswinters.textgeneration.domain.generators.databases.DeclarationFileTextGenerator;
import be.thomaswinters.textgeneration.domain.generators.named.NamedGeneratorRegister;
import be.thomaswinters.textgeneration.domain.parsers.DeclarationsFileParser;
import be.thomaswinters.twitter.util.TwitterUtil;
import be.thomaswinters.wikihow.WikiHowPageScraper;
import be.thomaswinters.wikihow.WikihowSearcher;
import be.thomaswinters.wikihow.data.Page;
import be.thomaswinters.wikihow.data.PageCard;
import org.jsoup.HttpStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class JeannineTipsGenerator implements IChatBot {
    private final WikihowSearcher searcher = WikihowSearcher.fromEnvironment("nl", Duration.ofSeconds(1));
    private final WikiHowPageScraper wikiHowPageScraper = new WikiHowPageScraper("nl");
    private final IFitnessFunction<String> tipFitnessFunction = e -> 1 / e.length();
    private final DutchFirstPersonConverter firstPersonConverter = new DutchFirstPersonConverter();
    private final ISelector<String> tipSelector = new TournamentSelection<>(tipFitnessFunction, 5);
    private final DeclarationFileTextGenerator templatedGenerator;
    private final ActionExtractor actionExtractor = new ActionExtractor();
    private Replacers tipNegators = new Replacers(Arrays.asList(
            new Replacer("een", "geen", false, true),
            new Replacer("goed", "slecht", false, true),
            new Replacer("de meeste", "zeer weinig", false, true),
            new Replacer("niet meer", "nog steeds", false, true),
            new Replacer("niet", "zeker wel", false, true),
            new Replacer("ook", "zeker niet", false, true)
    ));

    public JeannineTipsGenerator() throws IOException {
        List<CommandFactory> customCommands = Arrays.asList(
                new SingleTextGeneratorArgumentCommandFactory(
                        "firstToThirdMalePersonPronouns",
                        e -> new LambdaSingleGeneratorArgumentCommand(e,
                                firstPersonConverter::firstToThirdMalePersonPronouns,
                                "firstToThirdMalePersonPronouns")),
                new SingleTextGeneratorArgumentCommandFactory(
                        "firstToSecondPersonPronouns",
                        e -> new LambdaSingleGeneratorArgumentCommand(e,
                                firstPersonConverter::firstToSecondPersonPronouns,
                                "firstToSecondPersonPronouns"))
        );
        this.templatedGenerator = DeclarationsFileParser.createTemplatedGenerator(
                ClassLoader.getSystemResource("templates/jeannine.decl"),
                customCommands
        );
    }

    private List<Page> getPages(String search) throws IOException {
        List<String> searchWords = SentenceUtil.splitOnSpaces(search)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        List<PageCard> relatedPages = searcher.search(searchWords);
        return relatedPages
                .stream()
                // Sort by decreasing amount of matchine words
                .sorted(Comparator.comparingInt((PageCard e) -> {
                    List<String> words = SentenceUtil.splitOnSpaces(e.getTitle()).collect(Collectors.toList());
                    words.retainAll(searchWords);
                    return words.size();
                }).reversed())
                .map(e -> {
                    try {
                        return Optional.ofNullable(wikiHowPageScraper.scrape(e));
                    } catch (HttpStatusException httpEx) {
                        if (httpEx.getStatusCode() == 404) {
                            return Optional.<Page>empty();
                        }
                        throw new RuntimeException(httpEx);
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private List<String> getFirstTipsIn(List<Page> pages) {
        return pages
                .stream()
                .filter(e -> !e.getTips().isEmpty())
                .map(Page::getTips)
                .findFirst()
                .orElse(new ArrayList<>());
    }

    private String decapitalise(String input) {
        return Character.toLowerCase(input.charAt(0)) + input.substring(1);
    }

    private boolean isValidTip(String tip) {
        return !tip.matches(".*\\d+\\..*")
                && !tip.contains("http")
                && !tip.contains("deze methode")
                && !tip.contains("de methode")
                && !tip.contains("artikel")
                && !tip.contains("deze oefening");
    }

    private String cleanTip(String tip) {

        return tip
                .replaceAll("\\(.*\\)", "")
                .replaceAll("\\[.*\\]", "")
                .replaceAll("hij/zij", "hij")
                .replaceAll("hem/haar", "hem")
                .replaceAll("en/of", "en");


    }

    private String negateTip(String text) {
        String result = tipNegators.replace(text);
        System.out.println(
                "NEGATED TEXT:\t" + text + "\n" +
                        "TO RESULTING:\t" + result);
        return result;
    }

    @Override
    public Optional<String> generateReply(IChatMessage message) {
        String messageText = SentenceUtil.splitOnSpaces(message.getText())
                .filter(e -> !TwitterUtil.isTwitterWord(e))
                .collect(Collectors.joining(" "));
        System.out.println("REPLYIING TO " + messageText);

        NamedGeneratorRegister register = new NamedGeneratorRegister();

        String generatorToUse = "reply";
        Optional<ActionDescription> actionDescription = Optional.empty();

        if (message.getUser().getScreenName().toLowerCase().contains("octaaf")) {
            generatorToUse = "octaafReply";

            // Check if it contains an action
            if (messageText.contains("specialiteiten")) {
                String actionText = messageText
                        .substring(messageText.indexOf("zoals jij"), messageText.indexOf("...\" ja"))
//                        .replaceAll("[Aa]h,? ?", "")
                        .replaceAll("zoals jij", "")
                        .replaceAll("kan", "")
                        .replaceAll(TwitterUtil.TWITTER_USERNAME_REGEX, "");

                System.out.println("DETECTED ACTION: " + actionText);
                List<String> actionWords = SentenceUtil.splitOnSpaces(actionText).collect(Collectors.toList());
                String actionVerb = actionWords.get(actionWords.size() - 1);
                actionDescription = Optional.of(new ActionDescription(
                        actionVerb,
                        SentenceUtil.joinWithSpaces(actionWords.subList(0, actionWords.size() - 1))));

            } else {
                return Optional.empty();
            }
        } else {
            try {
                actionDescription = Picker.pickOptional(actionExtractor.extractAction(messageText));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("USING ACTION DESCRIPTION: " + actionDescription);

        if (actionDescription.isPresent()) {

            String actionVerb = actionDescription.get().getVerb();
            String actionSentence = actionDescription.get().getRestOfSentence().trim();
            String fullActionText = actionSentence + " " + actionVerb;

            register.createGenerator("actionVerb", actionVerb);
            if (actionSentence.length() > 0) {
                register.createGenerator("actionDescription", actionSentence);

            }


            List<String> tips = new ArrayList<>();
            try {
                tips = getFirstTipsIn(getPages(fullActionText))
                        .stream()
                        .map(SentenceUtil::getFirstSentence)
                        .map(String::trim)
                        .filter(e -> e.length() > 0)
                        .filter(this::isValidTip)
                        .map(this::decapitalise)
                        .map(this::cleanTip)
                        .collect(Collectors.toList());
            } catch (HttpStatusException e) {
                if (e.getStatusCode() == 404) {
                    System.out.println("404 for " + fullActionText);
                }
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("FOUND TIPS: " + tips);

            if (!tips.isEmpty()) {
                Optional<String> selectedTip = tipSelector.select(tips.stream());
                if (selectedTip.isPresent()) {
                    String tip = selectedTip.get();
                    // Check if action is something inverted (burgemeester)
                    if (fullActionText.contains("niet") || fullActionText.contains("geen")) {
                        tip = negateTip(tip);
                    }
                    register.createGenerator("tip", tip);
                    System.out.println("REGISTERED TIP:" + tip);
                }
            }
            try {
                System.out.println("GENERATING RESULT USING REGISTER: "+register);
                String result =
                        templatedGenerator.generate(generatorToUse,
                                new TextGeneratorContext(register, true)
                        );
                if (result.trim().length() > 0) {
                    return Optional.of(result);
                }
            } catch (RuntimeException e) {
                System.out.println("Error with Jeanninebot: " + e.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();


    }
}
