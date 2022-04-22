package corebot;

import arc.files.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.Streams;
import arc.util.serialization.*;
import arc.util.serialization.Jval.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.type.*;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.*;
import net.dv8tion.jda.api.events.guild.member.*;
import net.dv8tion.jda.api.events.guild.member.update.*;
import net.dv8tion.jda.api.events.message.*;
import net.dv8tion.jda.api.events.message.guild.*;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.*;
import net.dv8tion.jda.api.requests.*;
import net.dv8tion.jda.api.utils.*;
import net.dv8tion.jda.api.utils.cache.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import javax.imageio.*;
import java.awt.image.*;
import java.io.*;
import java.text.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.stream.*;
import java.util.zip.*;

import static corebot.CoreBot.*;

public class Messages extends ListenerAdapter{
	private static final int[][] allowedRanges =
	    { { 0x0020, 0x007E }, { 0x00A7, 0x00A7 }, { 0x00BC, 0x00BE }, { 0x0400, 0x045F } };
	private static final int maxNickLength = 32;
	private static final String invalidNicknameMessage = "Ваш никнейм содержит недопустимые символы." +
		" Разрешённые символы: ASCII, кириллица. Никнейм был изменён на ";
    private static final String prefix = "!";
    private static final int scamAutobanLimit = 3, pingSpamLimit = 10;
    private static final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
    private static final String[] warningStrings = {"однажды", "дважды", "трижды", "слишком много раз"};

    // https://stackoverflow.com/a/48769624
    private static final Pattern urlPattern = Pattern.compile("(?:(?:https?):\\/\\/)?[\\w/\\-?=%.]+\\.[\\w/\\-&?=%.]+");
    private static final Set<String> trustedDomains = Set.of(
        "discord.com",
        "discord.co",
        "discord.gg",
        "discord.media",
        "discord.gift",
        "discordapp.com",
        "discordapp.net",
        "discordstatus.com"
    );

    private static final Pattern invitePattern = Pattern.compile("(discord\\.gg/\\w|discordapp\\.com/invite/\\w|discord\\.com/invite/\\w)");
    private static final Pattern linkPattern = Pattern.compile("http(s?)://");
    private static final Pattern notScamPattern = Pattern.compile("discord\\.py|discord\\.js|nitrome\\.com");
    private static final Pattern scamPattern = Pattern.compile(String.join("|",
        "stea.*co.*\\.ru",
        "http.*stea.*c.*\\..*trad",
        "csgo.*kni[fv]e",
        "cs.?go.*inventory",
        "cs.?go.*cheat",
        "cheat.*cs.?go",
        "cs.?go.*skins",
        "skins.*cs.?go",
        "stea.*com.*partner",
        "скин.*partner",
        "steamcommutiny",
        "di.*\\.gift.*nitro",
        "http.*disc.*gift.*\\.",
        "free.*nitro.*http",
        "http.*free.*nitro.*",
        "nitro.*free.*http",
        "discord.*nitro.*free",
        "free.*discord.*nitro",
        "@everyone.*http",
        "http.*@everyone",
        "discordgivenitro",
        "http.*gift.*nitro",
        "http.*nitro.*gift",
        "http.*n.*gift",
        "бесплат.*нитро.*http",
        "нитро.*бесплат.*http",
        "nitro.*http.*disc.*nitro",
        "http.*click.*nitro",
        "http.*st.*nitro",
        "http.*nitro",
        "stea.*give.*nitro",
        "discord.*nitro.*steam.*get",
        "gift.*nitro.*http",
        "http.*discord.*gift",
        "discord.*nitro.*http",
        "personalize.*your*profile.*http",
        "nitro.*steam.*http",
        "steam.*nitro.*http",
        "nitro.*http.*d",
        "http.*d.*gift",
        "gift.*http.*d.*s",
        "discord.*steam.*http.*d",
        "nitro.*steam.*http",
        "steam.*nitro.*http",
        "dliscord.com",
        "free.*nitro.*http",
        "discord.*nitro.*http",
        "@everyone.*http",
        "http.*@everyone",
        "@everyone.*nitro",
        "nitro.*@everyone",
        "discord.*gi.*nitro"
    ));

    private final ObjectMap<String, UserData> userData = new ObjectMap<>();
    private final CommandHandler handler = new CommandHandler(prefix);
    private final CommandHandler adminHandler = new CommandHandler(prefix);
    private final JDA jda;

    public Guild guild;
    public TextChannel
    pluginChannel, crashReportChannel, announcementsChannel, artChannel,
    mapsChannel, moderationChannel, schematicsChannel, baseSchematicsChannel,
    logChannel, joinChannel, videosChannel, streamsChannel, testingChannel,
    alertsChannel, curatedSchematicsChannel;
    LongSeq schematicChannels = new LongSeq();

    public Messages(){
        String token = System.getenv("CORE_BOT_TOKEN");

        register();

        try{
            jda = JDABuilder.createDefault(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                .setMemberCachePolicy(MemberCachePolicy.ALL).disableCache(CacheFlag.VOICE_STATE).build();
            jda.awaitReady();
            jda.addEventListener(this);
            
            loadChannels();
            Log.info("Started validating nicknames.");
            guild.loadMembers(this::validateNickname);

            Log.info("Discord bot up.");
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }
    
    TextChannel channel(long id){
        return guild.getTextChannelById(id);
    }
    
    void loadChannels(){

        //all guilds and channels are loaded here for faster lookup
        guild = jda.getGuildById(953942350818856980L);
        pluginChannel = channel(966951464226422784L);
        crashReportChannel = channel(966947096890585128L);
        announcementsChannel = channel(959784619182542888L);
        artChannel = channel(966947096890585128L);
        mapsChannel = channel(966951369145745469L);
        moderationChannel = channel(953943536720564255L);
        schematicsChannel = channel(966951414830092288L);
        baseSchematicsChannel = channel(966947096890585128L);
        logChannel = channel(966935949508497479L);
        joinChannel = channel(953943577925414942L);
        streamsChannel = channel(966947096890585128L);
        videosChannel = channel(966947096890585128L);
        testingChannel = channel(966947096890585128L);
        alertsChannel = channel(966935949508497479L);
        curatedSchematicsChannel = channel(966947096890585128L);

        schematicChannels.add(schematicsChannel.getIdLong(), baseSchematicsChannel.getIdLong(), curatedSchematicsChannel.getIdLong());
    }

    void register(){
        handler.<Message>register("help", "Показывает все команды бота.", (args, msg) -> {
            StringBuilder builder = new StringBuilder();
            for(Command command : handler.getCommandList()){
                builder.append(prefix);
                builder.append("**");
                builder.append(command.text);
                builder.append("**");
                if(command.params.length > 0){
                    builder.append(" *");
                    builder.append(command.paramText);
                    builder.append("*");
                }
                builder.append(" - ");
                builder.append(command.description);
                builder.append("\n");
            }

            info(msg.getChannel(), "Commands", builder.toString());
        });

        handler.<Message>register("ping", "<ip>", "Отправляет пинг игровому серверу.", (args, msg) -> {
            if(!msg.getChannel().getName().equalsIgnoreCase("боты")){
                errDelete(msg, "Используйте команду в канале #боты.");
                return;
            }

            net.pingServer(args[0], result -> {
                if(result.name != null){
                    info(msg.getChannel(), "Сервер открыт", "Имя хоста: @\nИгроков: @\nКарта: @\nВолна: @\nВерсия: @\nЗадержка: @мс",
                    Strings.stripColors(result.name), result.players, Strings.stripColors(result.mapname), result.wave, result.version, result.ping);
                }else{
                    errDelete(msg, "Сервер закрыт", "Вышло время ожидания.");
                }
            });
        });

        handler.<Message>register("info", "<тема>", "Показывает информацию о какой-либо теме.", (args, msg) -> {
            try{
                Info info = Info.valueOf(args[0]);
                infoDesc(msg.getChannel(), info.title, info.text);
            }catch(IllegalArgumentException e){
                errDelete(msg, "Ошибка", "Тема '@' не существует.\nТемы: *@*", args[0], Arrays.toString(Info.values()));
            }
        });


        handler.<Message>register("postplugin", "<ссылка-github>", "Добавить плагин.", (args, msg) -> {
            if(!args[0].startsWith("https") || !args[0].contains("github")){
                errDelete(msg, "That's not a valid Github URL.");
            }else{
                try{
                    Document doc = Jsoup.connect(args[0]).get();

                    EmbedBuilder builder = new EmbedBuilder().setColor(normalColor).
                    setColor(normalColor)
                    .setAuthor(msg.getAuthor().getName(), msg.getAuthor().getEffectiveAvatarUrl(), msg.getAuthor().getEffectiveAvatarUrl())
                    .setTitle(doc.select("strong[itemprop=name]").text());

                    Elements elem = doc.select("span[itemprop=about]");
                    if(!elem.isEmpty()){
                        builder.addField("About", elem.text(), false);
                    }

                    builder
                    .addField("Link", args[0], false)
                    .addField("Downloads", args[0] + (args[0].endsWith("/") ? "" : "/") + "releases", false);

                    pluginChannel.sendMessageEmbeds(builder.build()).queue();

                    text(msg, "*Plugin posted.*");
                }catch(IOException e){
                    errDelete(msg, "Failed to fetch plugin info from URL.");
                }
            }
        });

        handler.<Message>register("postmap", "Отправить файл .msav в канал #карты.", (args, msg) -> {

            if(msg.getAttachments().size() != 1 || !msg.getAttachments().get(0).getFileName().endsWith(".msav")){
                errDelete(msg, "В сообщении должен быть прикреплён один файл формата .msav!");
                return;
            }

            Attachment a = msg.getAttachments().get(0);

            try{
                ContentHandler.Map map = contentHandler.readMap(net.download(a.getUrl()));
                new File("cache/").mkdir();
                File mapFile = new File("cache/" + a.getFileName());
                Fi imageFile = Fi.get("cache/image_" + a.getFileName().replace(".msav", ".png"));
                Streams.copy(net.download(a.getUrl()), new FileOutputStream(mapFile));
                ImageIO.write(map.image, "png", imageFile.file());

                EmbedBuilder builder = new EmbedBuilder().setColor(normalColor).setColor(normalColor)
                .setImage("attachment://" + imageFile.name())
                .setAuthor(msg.getAuthor().getName(), msg.getAuthor().getEffectiveAvatarUrl(), msg.getAuthor().getEffectiveAvatarUrl())
                .setTitle(map.name == null ? a.getFileName().replace(".msav", "") : map.name);

                if(map.description != null) builder.setFooter(map.description);

                mapsChannel.sendFile(mapFile).addFile(imageFile.file()).setEmbeds(builder.build()).queue();

                text(msg, "*Карта успешно отправлена.*");
            }catch(Exception e){
                String err = Strings.neatError(e, true);
                int max = 900;
                errDelete(msg, "Ошибка парсинга карты.", err.length() < max ? err : err.substring(0, max));
            }
        });

        handler.<Message>register("yandex", "<фраза...>", "Поищу за тебя это в Яндексе.", (args, msg) -> {
            text(msg, "https://yandex.ru/search/?text=@", Strings.encode(args[0]));
        });

        handler.<Message>register("cleanmod", "Очищает архив с модификацией формата `.zip`. Преобразовывает json в hjson и форматирует код.", (args, msg) -> {

            if(msg.getAttachments().size() != 1 || !msg.getAttachments().get(0).getFileName().endsWith(".zip")){
                errDelete(msg, "В сообщении должен быть прикреплён один файл формата .zip!");
                return;
            }

            Attachment a = msg.getAttachments().get(0);

            if(a.getSize() > 1024 * 1024 * 6){
                errDelete(msg, "Размер файла не может превышать 6 МБ.");
            }

            try{
                new File("cache/").mkdir();
                File baseFile = new File("cache/" + a.getFileName());
                Fi destFolder = new Fi("cache/dest_mod" + a.getFileName());
                Fi destFile = new Fi("cache/" + new Fi(baseFile).nameWithoutExtension() + "-cleaned.zip");

                if(destFolder.exists()) destFolder.deleteDirectory();
                if(destFile.exists()) destFile.delete();

                Streams.copy(net.download(a.getUrl()), new FileOutputStream(baseFile));
                ZipFi zip = new ZipFi(new Fi(baseFile.getPath()));
                zip.walk(file -> {
                    Fi output = destFolder.child(file.extension().equals("json") ? file.pathWithoutExtension() + ".hjson" : file.path());
                    output.parent().mkdirs();

                    if(file.extension().equals("json") || file.extension().equals("hjson")){
                        output.writeString(fixJval(Jval.read(file.readString())).toString(Jformat.hjson));
                    }else{
                        file.copyTo(output);
                    }
                });

                try(OutputStream fos = destFile.write(false, 2048); ZipOutputStream zos = new ZipOutputStream(fos)){
                    for(Fi add : destFolder.findAll(f -> true)){
                        if(add.isDirectory()) continue;
                        zos.putNextEntry(new ZipEntry(add.path().substring(destFolder.path().length())));
                        Streams.copy(add.read(), zos);
                        zos.closeEntry();
                    }

                }

                msg.getChannel().sendFile(destFile.file()).queue();

                text(msg, "*Архив с модификацией успешно очищен.*");
            }catch(Throwable e){
                errDelete(msg, "Ошибка парсинга мода.", Strings.neatError(e, false));
            }
        });

        handler.<Message>register("file", "<название...>", "Найти файл исходного кода Mindustry по имени", (args, msg) -> {
            //epic asynchronous code, I know
            Http.get("https://api.github.com/search/code?q=" +
            "filename:" + Strings.encode(args[0]) + "%20" +
            "repo:Anuken/Mindustry")
            .header("Accept", "application/vnd.github.v3+json")
            .error(err -> errDelete(msg, "Ошибка связи с GitHub", Strings.getSimpleMessage(err)))
            .block(result -> {
                msg.delete().queue();
                Jval val = Jval.read(result.getResultAsString());

                //merge with arc results
                Http.get("https://api.github.com/search/code?q=" +
                "filename:" + Strings.encode(args[0]) + "%20" +
                "repo:Anuken/Arc")
                .header("Accept", "application/vnd.github.v3+json")
                .block(arcResult -> {
                    Jval arcVal = Jval.read(arcResult.getResultAsString());

                    val.get("items").asArray().addAll(arcVal.get("items").asArray());
                    val.put("total_count", val.getInt("total_count", 0) + arcVal.getInt("total_count", 0));
                });

                int count = val.getInt("total_count", 0);

                if(count > 0){
                    val.get("items").asArray().removeAll(j -> !j.getString("name").contains(args[0]));
                    count = val.get("items").asArray().size;
                }

                if(count == 0){
                    errDelete(msg, "Результаты не найдены.");
                    return;
                }

                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(normalColor);
                embed.setAuthor(msg.getAuthor().getName() + ": Результаты поиска по GitHub", val.get("items").asArray().first().getString("html_url"), "https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png");
                embed.setTitle("Результаты поиска по GitHub");

                if(count == 1){
                    Jval item = val.get("items").asArray().first();
                    embed.setTitle(item.getString("name"));
                    embed.setDescription("[Просмотреть на GitHub](" + item.getString("html_url") + ")");
                }else{
                    int maxResult = 5, i = 0;
                    StringBuilder results = new StringBuilder();
                    for(Jval item : val.get("items").asArray()){
                        if(i++ > maxResult){
                            break;
                        }
                        results.append("[").append(item.getString("name")).append("]").append("(").append(item.getString("html_url")).append(")\n");
                    }

                    embed.setTitle((count > maxResult ? maxResult + "+" : count) + " результатов");
                    embed.setDescription(results.toString());
                }

                msg.getChannel().sendMessageEmbeds(embed.build()).queue();
            });
        });


        handler.<Message>register("mywarnings", "Получить информацию о своих предупреждениях. Только для #боты.", (args, msg) -> {
            if(!msg.getChannel().getName().equalsIgnoreCase("боты")){
                errDelete(msg, "Используйте команду в канале #боты.");
                return;
            }

            sendWarnings(msg, msg.getAuthor());
        });

        handler.<Message>register("avatar", "[@пользователь]", "Получить полную \"аватарку\" пользователя.", (args, msg) -> {
            if(!msg.getChannel().getName().equalsIgnoreCase("боты")){
                errDelete(msg, "Используйте команду в канале #боты.");
                return;
            }

            try{
                User user;
                if(args.length > 0){
                    long id;
                    try{
                        id = Long.parseLong(args[0]);
                    }catch(NumberFormatException e){
                        String author = args[0].substring(2, args[0].length() - 1);
                        if(author.startsWith("!")) author = author.substring(1);
                        id = Long.parseLong(author);
                    }

                    user = jda.retrieveUserById(id).complete();
                }else{
                    user = msg.getAuthor();
                }

                String link = user.getEffectiveAvatarUrl() + "?size=1024";

                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(normalColor);
                embed.setTitle(user.getName() + "#" + user.getDiscriminator());
                embed.setImage(link);
                embed.setDescription("[Ссылка](" + link + ")");
                embed.setFooter("Команду вызвал " + msg.getAuthor().getName() + "#" + msg.getAuthor().getDiscriminator());
                msg.getChannel().sendMessageEmbeds(embed.build()).queue();

            }catch(Exception e){
                errDelete(msg, "Неверный ID или имя пользователя.");
            }
        });

        adminHandler.<Message>register("userinfo", "<пользователь>", "Получить информацию о пользователе.", (args, msg) -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();

                if(user == null){
                    errDelete(msg, "Этого пользователя (ID @) нету в кэше. Как это вообще произошло?", l);
                }else{
                    Member member = guild.retrieveMember(user).complete();

                    info(msg.getChannel(), "Информация о пользователе " + member.getEffectiveName(),
                        "Никнейм: @\nНастоящий никнейм: @\nID: @\nСтатус: @\nРоли: @\nАдминистратор: @\nВремя прибытия: @",
                        member.getNickname(),
                        user.getName(),
                        member.getIdLong(),
                        member.getOnlineStatus(),
                        member.getRoles().stream().map(Role::getName).collect(Collectors.toList()),
                        isAdmin(user),
                        member.getTimeJoined()
                    );
                }
            }catch(Exception e){
                errDelete(msg, "Неверный ID или имя пользователя.");
            }
        });

        adminHandler.<Message>register("warnings", "<@пользователь>", "Получить количество предупреждений у пользователя.", (args, msg) -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();
                sendWarnings(msg, user);
            }catch(Exception e){
                errDelete(msg, "Неверный ID или имя пользователя.");
            }
        });

        adminHandler.<Message>register("delete", "<amount>", "Удалить несколько сообщений.", (args, msg) -> {
            try{
                int number = Integer.parseInt(args[0]);
                MessageHistory hist = msg.getChannel().getHistoryBefore(msg, number).complete();
                msg.delete().queue();
                msg.getTextChannel().deleteMessages(hist.getRetrievedHistory()).queue();
            }catch(NumberFormatException e){
                errDelete(msg, "Неверное число.");
            }
        });

        adminHandler.<Message>register("warn", "<@пользователь> [причина...]", "Дать предупреждение пользователю.", (args, msg) -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();
                var list = getWarnings(user);
                list.add(System.currentTimeMillis() + ":::" + msg.getAuthor().getName() + (args.length > 1 ? ":::" + args[1] : ""));
                text(msg, "**@**, вы были предупреждены *@*.", user.getAsMention(), warningStrings[Mathf.clamp(list.size - 1, 0, warningStrings.length - 1)]);
                prefs.putArray("warning-list-" + user.getIdLong(), list);
                if(list.size >= 3){
                    moderationChannel.sendMessage("User " + user.getAsMention() + " has been warned 3 or more times!").queue();
                }
            }catch(Exception e){
                errDelete(msg, "Неверный ID или имя пользователя.");
            }
        });

        adminHandler.<Message>register("clearwarnings", "<@пользователь>", "Очистить все предупреждения.", (args, msg) -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();
                prefs.putArray("warning-list-" + user.getIdLong(), new Seq<>());
                text(msg, "Cleared warnings for user '@'.", user.getName());
            }catch(Exception e){
                errDelete(msg, "Неверный ID или имя пользователя.");
            }
        });

        /*adminHandler.<Message>register("schemdesigner", "<add/remove> <@user>", "Make a user a verified schematic designer.", (args, msg) -> {
            String author = args[1].substring(2, args[1].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = jda.retrieveUserById(l).complete();
                boolean add = args[0].equals("add");
                if(add){
                    guild.addRoleToMember(l, guild.getRoleById(877171645427621889L)).queue();
                }else{
                    guild.removeRoleFromMember(l, guild.getRoleById(877171645427621889L)).queue();
                }

                text(msg, "**@** is @ a verified schematic designer.", user.getName(), add ? "now" : "no longer");
            }catch(Exception e){
                errDelete(msg, "Incorrect name format.");
            }
        });*/
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        var msg = event.getMessage();

        if(msg.getAuthor().isBot() || msg.getChannel().getType() != ChannelType.TEXT) return;

        EmbedBuilder log = new EmbedBuilder()
        .setAuthor(msg.getAuthor().getName(), msg.getAuthor().getEffectiveAvatarUrl(), msg.getAuthor().getEffectiveAvatarUrl())
        .setDescription(msg.getContentRaw().length() >= 2040 ? msg.getContentRaw().substring(0, 2040) + "..." : msg.getContentRaw())
        .addField("Автор", msg.getAuthor().getAsMention(), false)
        .addField("Канал", msg.getTextChannel().getAsMention(), false)
        .setColor(normalColor);

        if(msg.getReferencedMessage() != null){
            log.addField("Отвечает на сообщение", msg.getReferencedMessage().getAuthor().getAsMention() + " [Ссылка](" + msg.getReferencedMessage().getJumpUrl() + ")", false);
        }

        if(msg.getMentionedUsers().stream().anyMatch(u -> u.getIdLong() == 201731509223292928L)){
            log.addField("Заметка", "упоминание", false);
        }

        if(msg.getChannel().getIdLong() != testingChannel.getIdLong()){
            logChannel.sendMessageEmbeds(log.build()).queue();
        }

        //delete stray invites
        if(!isAdmin(msg.getAuthor()) && checkSpam(msg, false)){
            return;
        }

        //delete non-art
        /*if(!isAdmin(msg.getAuthor()) && msg.getChannel().getIdLong() == artChannel.getIdLong() && msg.getAttachments().isEmpty()){
            msg.delete().queue();

            if(msg.getType() != MessageType.CHANNEL_PINNED_ADD){
                try{
                    msg.getAuthor().openPrivateChannel().complete().sendMessage("Don't send messages without images in that channel.").queue();
                }catch(Exception e1){
                    e1.printStackTrace();
                }
            }
        }*/

        String text = msg.getContentRaw();

        //schematic preview
        if((msg.getContentRaw().startsWith(ContentHandler.schemHeader) && msg.getAttachments().isEmpty()) ||
        (msg.getAttachments().size() == 1 && msg.getAttachments().get(0).getFileExtension() != null && msg.getAttachments().get(0).getFileExtension().equals(Vars.schematicExtension))){
            try{
                Schematic schem = msg.getAttachments().size() == 1 ? contentHandler.parseSchematicURL(msg.getAttachments().get(0).getUrl()) : contentHandler.parseSchematic(msg.getContentRaw());
                BufferedImage preview = contentHandler.previewSchematic(schem);
                String sname = schem.name().replace("/", "_").replace(" ", "_");
                if(sname.isEmpty()) sname = "empty";

                new File("cache").mkdir();
                File previewFile = new File("cache/img_" + UUID.randomUUID() + ".png");
                File schemFile = new File("cache/" + sname + "." + Vars.schematicExtension);
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(normalColor).setColor(normalColor)
                .setImage("attachment://" + previewFile.getName())
                .setAuthor(msg.getAuthor().getName(), msg.getAuthor().getEffectiveAvatarUrl(), msg.getAuthor().getEffectiveAvatarUrl()).setTitle(schem.name());

                if(!schem.description().isEmpty()) builder.setFooter(schem.description());

                StringBuilder field = new StringBuilder();

                for(ItemStack stack : schem.requirements()){
                    List<Emote> emotes = guild.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? guild.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                }
                builder.addField("Стоимость", field.toString(), false);

                msg.getChannel().sendFile(schemFile).addFile(previewFile).setEmbeds(builder.build()).queue();
                msg.delete().queue();
            }catch(Throwable e){
                if(schematicChannels.contains(msg.getChannel().getIdLong())){
                    msg.delete().queue();
                    try{
                        msg.getAuthor().openPrivateChannel().complete().sendMessage("Ошибка в парсинге схемы: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")")).queue();
                    }catch(Exception e2){
                        e2.printStackTrace();
                    }
                }
                //ignore errors
            }
        }else if(schematicChannels.contains(msg.getChannel().getIdLong()) && !isAdmin(msg.getAuthor())){
            //delete non-schematics
            msg.delete().queue();
            try{
                msg.getAuthor().openPrivateChannel().complete().sendMessage("В канал #схемы можно отправлять только схемы. Вы можете отправить их как файл, или как кодированную строку.").queue();
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }

        if(!text.replace(prefix, "").trim().isEmpty()){
            if(isAdmin(msg.getAuthor())){
                boolean unknown = handleResponse(msg, adminHandler.handleMessage(text, msg), false);
                handleResponse(msg, handler.handleMessage(text, msg), !unknown);
            }else{
                handleResponse(msg, handler.handleMessage(text, msg), true);
            }
        }
    }

    @Override
    public void onGuildMessageUpdate(GuildMessageUpdateEvent event){
        var msg = event.getMessage();

        if(isAdmin(msg.getAuthor()) || checkSpam(msg, true)){
            return;
        }

        /*if((msg.getChannel().getIdLong() == artChannel.getIdLong()) && msg.getAttachments().isEmpty()){
            msg.delete().queue();
            try{
                msg.getAuthor().openPrivateChannel().complete().sendMessage("Don't send messages without images in that channel.").queue();
            }catch(Exception e){
                e.printStackTrace();
            }
        }*/
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event){
        event.getUser().openPrivateChannel().complete().sendMessage(
        """
        **Добро пожаловать в русскоязычный дискорд Mindustry.**
                
        *Напоминаем прочитать #правила и описания каналов до отправления сообщений.*
                
        **Посмотреть все часто задаваемые вопросы можно здесь:**
        <https://discord.com/channels/953942350818856980/953955256147009538/953955996651356182>
        """
        ).queue();
        validateNickname(event.getMember());
        joinChannel
        .sendMessageEmbeds(new EmbedBuilder()
            .setAuthor(event.getUser().getName(), event.getUser().getAvatarUrl(), event.getUser().getAvatarUrl())
            .addField("Пользователь", event.getUser().getAsMention(), false)
            .addField("ID", "`" + event.getUser().getId() + "`", false)
            .setColor(normalColor).build())
        .queue();
    }
    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event){
        validateNickname(event.getMember());
    }

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event){
        guild.retrieveMember(event.getUser()).queue(this::validateNickname, t -> {});
    }
    public void validateNickname(Member member){
        char[] nick = member.getEffectiveName().toCharArray();
        if(!fixNickname(nick) && guild.getSelfMember().canInteract(member)) {
            String newNick = (new String(nick)).substring(0, maxNickLength-1);
	        member.modifyNickname(newNick).queue();
            member.getUser().openPrivateChannel()
                .flatMap(c -> c.sendMessage(invalidNicknameMessage + newNick))
                .queue(m -> {}, t -> {});
    	}
    }

    public static boolean fixNickname(char[] nickname) {
        boolean allowed = true;
        for (int i = 0; i < nickname.length; i++) {
            boolean allowedCharacter = false;
            for (int[] range : allowedRanges) {
                if (nickname[i] >= range[0] && nickname[i] <= range[1]) {
                    allowedCharacter = true;
                    break;
                }
            }
            if (!allowedCharacter) {
                allowed = false;
                nickname[i] = '?';
            }
        }
        return allowed;
    }

    void sendWarnings(Message msg, User user){
        var list = getWarnings(user);
        text(msg, "У пользователя '@' **@** @.\n@", user.getName(), list.size, list.size == 1 ? "предупреждение" : "предупреждения",
        list.map(s -> {
            String[] split = s.split(":::");
            long time = Long.parseLong(split[0]);
            String warner = split.length > 1 ? split[1] : null, reason = split.length > 2 ? split[2] : null;
            return "- `" + fmt.format(new Date(time)) + "`: Срок истечёт через " + (warnExpireDays - Duration.ofMillis((System.currentTimeMillis() - time)).toDays()) + " дней" +
            (warner == null ? "" : "\n  ↳ *От:* " + warner) +
            (reason == null ? "" : "\n  ↳ *Причина:* " + reason);
        }).toString("\n"));
    }

    public void text(MessageChannel channel, String text, Object... args){
        channel.sendMessage(Strings.format(text, args)).queue();
    }

    public void text(Message message, String text, Object... args){
        text(message.getChannel(), text, args);
    }

    public void info(MessageChannel channel, String title, String text, Object... args){
        channel.sendMessageEmbeds(new EmbedBuilder().addField(title, Strings.format(text, args), true).setColor(normalColor).build()).queue();
    }

    public void infoDesc(MessageChannel channel, String title, String text, Object... args){
        channel.sendMessageEmbeds(new EmbedBuilder().setTitle(title).setDescription(Strings.format(text, args)).setColor(normalColor).build()).queue();
    }

    /** Sends an error, deleting the base message and the error message after a delay. */
    public void errDelete(Message message, String text, Object... args){
        errDelete(message, "Ошибка", text, args);
    }

    /** Sends an error, deleting the base message and the error message after a delay. */
    public void errDelete(Message message, String title, String text, Object... args){
        message.getChannel().sendMessageEmbeds(new EmbedBuilder()
        .addField(title, Strings.format(text, args), true).setColor(errorColor).build())
        .queue(result -> result.delete().queueAfter(messageDeleteTime, TimeUnit.MILLISECONDS));

        //delete base message too
        message.delete().queueAfter(messageDeleteTime, TimeUnit.MILLISECONDS);
    }

    private Seq<String> getWarnings(User user){
        var list = prefs.getArray("warning-list-" + user.getIdLong());
        //remove invalid warnings
        list.removeAll(s -> {
            String[] split = s.split(":::");
            return Duration.ofMillis((System.currentTimeMillis() - Long.parseLong(split[0]))).toDays() >= warnExpireDays;
        });

        return list;
    }

    private Jval fixJval(Jval val){
        if(val.isArray()){
            Seq<Jval> list = val.asArray().copy();
            for(Jval child : list){
                if(child.isObject() && (child.has("item")) && child.has("amount")){
                    val.asArray().remove(child);
                    val.asArray().add(Jval.valueOf(child.getString("item", child.getString("liquid", "")) + "/" + child.getInt("amount", 0)));
                }else{
                    fixJval(child);
                }
            }
        }else if(val.isObject()){
            Seq<String> keys = val.asObject().keys().toArray();

            for(String key : keys){
                Jval child = val.get(key);
                if(child.isObject() && (child.has("item")) && child.has("amount")){
                    val.remove(key);
                    val.add(key, Jval.valueOf(child.getString("item", child.getString("liquid", "")) + "/" + child.getInt("amount", 0)));
                }else{
                    fixJval(child);
                }
            }
        }

        return val;
    }

    boolean isAdmin(User user){
        var member = guild.retrieveMember(user).complete();
        return member != null && member.getRoles().stream().anyMatch(role -> role.getName().equals("Разработчик") || role.getName().equals("Модератор") || role.getName().equals("\uD83D\uDD28 \uD83D\uDD75️\u200D♂️"));
    }

    boolean checkSpam(Message message, boolean edit){

        if(message.getChannel().getType() != ChannelType.PRIVATE){
            Seq<String> mentioned =
                //ignore reply messages, bots don't use those
                message.getReferencedMessage() != null ? new Seq<>() :
                //get all mentioned members and roles in one list
                Seq.with(message.getMentionedMembers()).map(IMentionable::getAsMention).and(Seq.with(message.getMentionedRoles()).map(IMentionable::getAsMention));

            var data = data(message.getAuthor());
            String content = message.getContentRaw().toLowerCase(Locale.ROOT);

            //go through every ping individually
            for(var ping : mentioned){
                if(!ping.equals(data.lastPingId)){
                    data.lastPingId = ping;
                    data.uniquePings++;
                    if(data.uniquePings >= pingSpamLimit){
                        Log.info("Автоматически блокирую пользователя @ за спам @ пингов подряд.", message.getAuthor().getName() + "#" + message.getAuthor().getId(), data.uniquePings);
                        alertsChannel.sendMessage(message.getAuthor().getAsMention() + " **был автоматически заблокирован из-за упоминания " + pingSpamLimit + " пользователей в одном сообщении!**").queue();
                        message.getGuild().ban(message.getAuthor(), 1, "Autobanned for spamming member pings.").queue();
                    }
                }
            }

            if(mentioned.isEmpty()){
                data.uniquePings = 0;
            }

            //check for consecutive links
            if(!edit && linkPattern.matcher(content).find()){

                if(content.equals(data.lastLinkMessage) && !message.getChannel().getId().equals(data.lastLinkChannelId)){
                    Log.warn("Пользователь @ заспамил ссылку в канале @ (сообщение: @): '@'", message.getAuthor().getName(), message.getChannel().getName(), message.getId(), content);

                    //only start deleting after 2 posts
                    if(data.linkCrossposts >= 1){
                        alertsChannel.sendMessage(
                            message.getAuthor().getAsMention() +
                            " **спамит ссылки** в " + message.getTextChannel().getAsMention() +
                            ":\n\n" + message.getContentRaw()
                        ).queue();

                        message.delete().queue();
                        message.getAuthor().openPrivateChannel().complete().sendMessage("Вы отправили ссылку несколько раз. Прекратите, иначе **вас автоматически заблокируют.**").queue();
                    }

                    //4 posts = ban
                    if(data.linkCrossposts ++ >= 3){
                        Log.warn("Пользователь @ (@) был заблокирован из-за спама ссылок.", message.getAuthor().getName(), message.getAuthor().getAsMention());

                        alertsChannel.sendMessage(message.getAuthor().getAsMention() + " **был заблокирован из-за спама ссылок!**").queue();
                        message.getGuild().ban(message.getAuthor(), 1, "Autobanned for spam-posting links.").queue();
                    }
                }

                data.lastLinkMessage = content;
                data.lastLinkChannelId = message.getChannel().getId();
            }else{
                data.linkCrossposts = 0;
                data.lastLinkMessage = null;
                data.lastLinkChannelId = null;
            }

            if(invitePattern.matcher(content).find()){
                Log.warn("Пользователь @ отправил приглашение в канал @.", message.getAuthor().getName(), message.getChannel().getName());
                message.delete().queue();
                message.getAuthor().openPrivateChannel().complete().sendMessage("Не отправляйте случайные приглашения на русскоязычном сервере Mindustry! Читайте правила.").queue();
                return true;
            }else if(containsScamLink(message)){
                Log.warn("Пользователь @ отправил потенциально вредоносное сообщение в @: '@'", message.getAuthor().getName(), message.getChannel().getName(), message.getContentRaw());

                int count = data.scamMessages ++;

                alertsChannel.sendMessage(
                    message.getAuthor().getAsMention() +
                    " **отправил потенциально вредоносное сообщение ** в " + message.getTextChannel().getAsMention() +
                    ":\n\n" + message.getContentRaw()
                ).queue();

                message.delete().queue();
                message.getAuthor().openPrivateChannel().complete().sendMessage("Ваше сообщение было определено системой как вредоносное. Не отправляйте подобные сообщения, иначе **вас автоматически заблокируют.").queue();

                if(count >= scamAutobanLimit - 1){
                    Log.warn("Пользователь @ (@) был автоматически заблокирован после @ вредоносных сообщений.", message.getAuthor().getName(), message.getAuthor().getAsMention(), count + 1);

                    alertsChannel.sendMessage(message.getAuthor().getAsMention() + " **был автоматически заблокирован после отправления" + scamAutobanLimit + " вредоносных сообщений!**").queue();
                    message.getGuild().ban(message.getAuthor(), 0, "[Auto-Ban] Posting several potential scam messages in a row.").queue();
                }

                return true;
            }else{
                //non-consecutive scam messages don't count
                data.scamMessages = 0;
            }

        }
        return false;
    }

    boolean handleResponse(Message msg, CommandResponse response, boolean logUnknown){
        if(response.type == ResponseType.unknownCommand){
            if(logUnknown){
                errDelete(msg, "Ошибка", "Неизвестная команда. Выполните !help для списка команд.");
            }
            return false;
        }else if(response.type == ResponseType.manyArguments || response.type == ResponseType.fewArguments){
            if(response.command.params.length == 0){
                errDelete(msg, "Неверные аргументы.", "Формат: @@", prefix, response.command.text);
            }else{
                errDelete(msg, "Неверные аргументы.", "Формат: @@ *@*", prefix, response.command.text, response.command.paramText);
            }
            return false;
        }
        return true;
    }

    boolean containsScamLink(Message message){
        String content = message.getContentRaw().toLowerCase(Locale.ROOT);

        //some discord-related keywords are never scams (at least, not from bots)
        if(notScamPattern.matcher(content).find()){
            return false;
        }

        // Regular check
        if(scamPattern.matcher(content.replace("\n", " ")).find()){
            return true;
        }

        // Extracts the urls of the message
        List<String> urls = urlPattern.matcher(content).results().map(MatchResult::group).toList();

        for(String url : urls){
            // Gets the domain and splits its different parts
            String[] rawDomain = url
                    .replace("https://", "")
                    .replace("http://", "")
                    .split("/")[0]
                    .split("\\.");

            // Gets rid of the subdomains
            rawDomain = Arrays.copyOfRange(rawDomain, Math.max(rawDomain.length - 2, 0), rawDomain.length);

            // Re-assemble
            String domain = String.join(".", rawDomain);

            // Matches slightly altered links
            if(!trustedDomains.contains(domain) && trustedDomains.stream().anyMatch(genuine -> Strings.levenshtein(genuine, domain) <= 2)){
                return true;
            }
        }

        return false;
    }

    UserData data(User user){
        return userData.get(user.getId(), UserData::new);
    }

    static class UserData{
        /** consecutive scam messages sent */
        int scamMessages;
        /** last message that contained any link */
        @Nullable String lastLinkMessage;
        /** channel ID of last link posted */
        @Nullable String lastLinkChannelId;
        /** link cross-postings in a row */
        int linkCrossposts;
        /** ID of last member pinged */
        String lastPingId;
        /** number of unique members pinged in a row */
        int uniquePings;
    }
}
