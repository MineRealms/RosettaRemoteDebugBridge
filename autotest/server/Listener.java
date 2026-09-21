package rosetta.server;

import com.rosetta.remotedebugbridge.eventbus.RosettaEventSubscriber;
import com.rosetta.remotedebugbridge.eventbus.RosettaSubscribeEvent;

@RosettaEventSubscriber
public class Listener {
    public static final class Ping {
    }

    @RosettaSubscribeEvent
    public static void onPing(Ping event) {
    }
}
