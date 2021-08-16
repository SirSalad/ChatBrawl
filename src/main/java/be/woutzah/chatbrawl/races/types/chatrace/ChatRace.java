package be.woutzah.chatbrawl.races.types.chatrace;

import be.woutzah.chatbrawl.ChatBrawl;
import be.woutzah.chatbrawl.files.ConfigType;
import be.woutzah.chatbrawl.leaderboard.LeaderboardManager;
import be.woutzah.chatbrawl.leaderboard.LeaderboardStatistic;
import be.woutzah.chatbrawl.races.RaceManager;
import be.woutzah.chatbrawl.races.types.Race;
import be.woutzah.chatbrawl.races.types.RaceType;
import be.woutzah.chatbrawl.rewards.RewardManager;
import be.woutzah.chatbrawl.settings.GeneralSetting;
import be.woutzah.chatbrawl.settings.SettingManager;
import be.woutzah.chatbrawl.settings.races.ChatRaceSetting;
import be.woutzah.chatbrawl.settings.races.RaceSetting;
import be.woutzah.chatbrawl.time.TimeManager;
import be.woutzah.chatbrawl.util.FireWorkUtil;
import be.woutzah.chatbrawl.util.Printer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChatRace extends Race {

    private final List<WordToGuess> wordToGuessList;
    private WordToGuess wordToGuess;

    public ChatRace(RaceManager raceManager, SettingManager settingManager,
                    RewardManager rewardManager, TimeManager timeManager,
                    LeaderboardManager leaderboardManager) {
        super(RaceType.CHAT, raceManager, settingManager, rewardManager, timeManager, leaderboardManager);
        this.wordToGuessList = new ArrayList<>();
        initWordToGuessList();
    }

    private void initWordToGuessList() {
        settingManager.getConfigSection(ChatRaceSetting.WORDS).getKeys(false).forEach(entry -> {
            String word = settingManager.getString(ConfigType.CHATRACE, "words." + entry + ".word");
            List<Integer> rewardIds = settingManager.getIntegerList(ConfigType.CHATRACE, "words." + entry + ".rewards");
            wordToGuessList.add(new WordToGuess(word, rewardIds));
        });
    }

    public void initRandomWord() {
        wordToGuess = wordToGuessList.get(random.nextInt(wordToGuessList.size()));
    }

    @Override
    public void beforeRaceStart() {
        initRandomWord();
        if (isAnnounceStartEnabled()) announceStart(isCenterMessages());
        if (isActionBarEnabled()) showActionbar();
    }

    @EventHandler
    public void checkWordInChat(AsyncPlayerChatEvent e) {
        //do checks
        if (!isActive()) return;
        Player player = e.getPlayer();
        if (!raceManager.isCreativeAllowed()) {
            if (player.getGameMode() == GameMode.CREATIVE) return;
        }
        World world = player.getWorld();
        if (!raceManager.isWorldAllowed(world.getName())) return;
        String message = Printer.stripColors(e.getMessage());
        if (raceManager.startsWithForbiddenCommand(message)) return;
        if (!message.equals(wordToGuess.getWord())) return;
        //when correct
        afterRaceEnd();
        if (isAnnounceEndEnabled()) announceWinner(isCenterMessages(), player);
        if (isFireWorkEnabled()) FireWorkUtil.shootFireWorkSync(player);
        this.raceTask.cancel();
        rewardManager.executeRandomRewardSync(wordToGuess.getRewardIds(), player);
        if (settingManager.getBoolean(GeneralSetting.MYSQL_ENABLED)) {
            leaderboardManager.addWin(new LeaderboardStatistic(player.getUniqueId(), type, timeManager.getTotalSeconds()));
        }
        Printer.sendMessage(getWinnerPersonal(), player);
    }

    @Override
    public void announceStart(boolean center) {
        List<String> messageList = settingManager.getStringList(RaceType.CHAT, RaceSetting.LANGUAGE_START)
                .stream()
                .map(this::replacePlaceholders)
                .collect(Collectors.toList());
        if (center) {
            Printer.broadcast(Printer.centerMessage(messageList));
            return;
        }
        Printer.broadcast(messageList);
    }

    @Override
    public void sendStart(Player player) {
        List<String> messageList = settingManager.getStringList(RaceType.CHAT, RaceSetting.LANGUAGE_START)
                .stream()
                .map(this::replacePlaceholders)
                .collect(Collectors.toList());
        if (isCenterMessages()) {
            Printer.sendMessage(Printer.centerMessage(messageList), player);
            return;
        }
        Printer.sendMessage(messageList, player);
    }

    @Override
    public void showActionbar() {
        String message = replacePlaceholders(settingManager.getString(RaceType.CHAT, RaceSetting.LANGUAGE_ACTIONBAR));
        this.actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getOnlinePlayers().forEach(p -> p.spigot()
                        .sendMessage(ChatMessageType.ACTION_BAR,
                                new TextComponent(Printer.parseColor(message))));
            }
        }.runTaskTimer(ChatBrawl.getInstance(), 0, 20);
    }

    @Override
    public String replacePlaceholders(String message) {
        return message.replace("<word>", wordToGuess.getWord());
    }

    @Override
    public void announceWinner(boolean center, Player player) {
        List<String> messageList = settingManager.getStringList(RaceType.CHAT, RaceSetting.LANGUAGE_WINNER)
                .stream()
                .map(this::replacePlaceholders)
                .map(s -> s.replace("<displayname>", player.getDisplayName()))
                .map(s -> s.replace("<player>", player.getName()))
                .map(s -> s.replace("<time>", timeManager.getTimeString()))
                .collect(Collectors.toList());
        if (center) {
            Printer.broadcast(Printer.centerMessage(messageList));
            return;
        }
        Printer.broadcast(messageList);
    }
}
