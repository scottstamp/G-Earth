package g_earth.extensions.examples.speechcolorizer;

import g_earth.extensions.Extension;
import g_earth.extensions.ExtensionInfo;
import g_earth.protocol.HMessage;
import g_earth.protocol.HPacket;

import java.util.Random;

/**
 * Created by Jonas on 24/06/18.
 */

/**
 *  - getTitle(), getDescription(), getVersion() and getAuthor() must be implemented
 *
 */

@ExtensionInfo(
        Title = "Colorize me!",
        Description = "Because we like to be weird",
        Version = "1.0",
        Author = "sirjonasxx"
)
public class SpeechColorizer extends Extension {

    public static void main(String[] args) {
        new SpeechColorizer(args).run();
    }
    private SpeechColorizer(String[] args) {
        super(args);
    }

    private static final int SPEECH_ID = 3373;
    private static final String[] COLORS = {"red", "blue", "cyan", "green", "purple"};
    private static final Random r = new Random();

    @Override
    protected void init() {
        intercept(HMessage.Side.TOSERVER, SPEECH_ID, this::onSendMessage);
        System.out.println("test");
    }

    private void onSendMessage(HMessage message) {
        HPacket packet = message.getPacket();

        String speechtext = packet.readString();
        System.out.println("you said: " + speechtext);

        message.setBlocked(speechtext.equals("blocked"));
        packet.replaceString(6, "@" + COLORS[r.nextInt(COLORS.length)] + "@" + speechtext);
    }

    protected String getTitle() {
        return "Colorize me!";
    }
    protected String getDescription() {
        return "Because we like to be weird";
    }
    protected String getVersion() {
        return "1.0";
    }
    protected String getAuthor() {
        return "sirjonasxx";
    }
}
