package rainjava.server;

import net.rain.eventbus.RainEventSubscriber;
import net.rain.eventbus.RainSubscribeEvent;

@RainEventSubscriber
public class Listener {
    public static final class Ping {
    }

    @RainSubscribeEvent
    public static void onPing(Ping event) {
    }
}
