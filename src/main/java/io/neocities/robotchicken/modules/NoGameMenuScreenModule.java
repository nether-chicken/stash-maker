package io.neocities.robotchicken.modules;

public class NoGameMenuScreenModule extends Module {

    public static final NoGameMenuScreenModule INSTANCE;

    static {
        INSTANCE = new NoGameMenuScreenModule();
    }

    private NoGameMenuScreenModule() {
    }

    @Override
    public void onTick() {
//        if (MC.inGame() && mc.currentScreen instanceof GameMenuScreen) {
//            mc.currentScreen.close();
//        }
    }
}
