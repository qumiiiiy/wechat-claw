package com.weida.bot.wechatilinkbot.model;

/**
 * Provider-neutral voice preference retained per conversation.
 */
public enum VoicePreference {

    /** Provider default. */
    DEFAULT("default"),

    /** Generic male voice. */
    MALE("male"),

    /** Generic female voice. */
    FEMALE("female"),

    /** Generic child voice. */
    CHILD("child"),

    /** Boy or young-teen profile. */
    BOY("boy"),

    /** Girl profile. */
    GIRL("girl"),

    /** Young male profile. */
    YOUNG_MALE("young-male"),

    /** Young female profile. */
    YOUNG_FEMALE("young-female"),

    /** Mature male profile. */
    MATURE_MALE("mature-male"),

    /** Mature female profile. */
    MATURE_FEMALE("mature-female"),

    /** Elderly male profile. */
    ELDERLY_MALE("elderly-male"),

    /** Elderly female profile. */
    ELDERLY_FEMALE("elderly-female"),

    /** Energetic female profile. */
    ENERGETIC_FEMALE("energetic-female"),

    /** Sweet female profile. */
    SWEET_FEMALE("sweet-female"),

    /** Gentle female profile. */
    GENTLE_FEMALE("gentle-female"),

    /** Warm male profile. */
    WARM_MALE("warm-male"),

    /** Intellectual female profile. */
    INTELLECTUAL_FEMALE("intellectual-female"),

    /** Male narrator profile. */
    NARRATOR_MALE("narrator-male"),

    /** Female narrator profile. */
    NARRATOR_FEMALE("narrator-female"),

    /** Professional female profile. */
    PROFESSIONAL_FEMALE("professional-female"),

    /** Live-stream female profile. */
    LIVE_STREAM_FEMALE("live-stream-female"),

    /** Robot profile. */
    ROBOT("robot"),

    /** Monkey King character profile. */
    MONKEY_KING("monkey-king"),

    /** Cantonese male profile. */
    CANTONESE_MALE("cantonese-male"),

    /** Shaanxi male profile. */
    SHAANXI_MALE("shaanxi-male"),

    /** Minnan female profile. */
    MINNAN_FEMALE("minnan-female");

    private final String configKey;

    VoicePreference(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}
