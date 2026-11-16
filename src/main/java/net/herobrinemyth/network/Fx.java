package net.herobrinemyth.network;

/** Client effect ids. */
public final class Fx {
    /** Mutes every sound in the game except Herobrine's. */
    public static final int SILENCE = 0;
    /** Fog closes in. intensity 0..1. */
    public static final int FOG = 1;
    /** His face, full screen. */
    public static final int JUMPSCARE = 2;
    /** Camera shake. */
    public static final int SHAKE = 3;
    /** Screen goes black. */
    public static final int BLACKOUT = 4;
    /** Screen corruption / static. */
    public static final int GLITCH = 5;
    /** Lose control of your body. x/y/z = what you are forced to look at, extra = 1 walk towards it. */
    public static final int POSSESS = 6;
    /** "Crash" the game (safely). */
    public static final int CRASH = 7;
    /** Take the world. */
    public static final int DELETE_WORLD = 8;
    /** Red flash. */
    public static final int RED_FLASH = 9;
    /** Very short blink of darkness. */
    public static final int BLINK = 10;
    /** Heartbeat for a while. */
    public static final int HEARTBEAT = 11;
    /** Clears every running effect. */
    public static final int CLEAR = 12;
    /** Forces the camera to slowly turn and look at x/y/z without taking movement. */
    public static final int LOOK_AT = 13;
    /** Spun around by something you cannot see. */
    public static final int SPIN = 14;
    /** Blood rain: red rain, red dark fog. */
    public static final int BLOOD = 15;
    /** The server tells the client which day of the haunting it is (intensity = day, extra = flags). */
    public static final int MOOD = 16;
    /** Around x/y/z, for `duration` ticks and `intensity` blocks, the game's own sounds play at full volume (he caused them). */
    public static final int UNMUTE = 17;
    /** Thrown out to the title screen with one of his messages (intensity = which message). The world is saved. */
    public static final int KICK = 18;

    private Fx() {}
}
