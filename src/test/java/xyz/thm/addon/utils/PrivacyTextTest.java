/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivacyTextTest {
    @Test
    void spawnAndAxisPavementCountAsMainHighway() {
        assertTrue(PrivacyText.isMainHighwayPosition(0, 0));
        assertTrue(PrivacyText.isMainHighwayPosition(12_000, 0));
        assertTrue(PrivacyText.isMainHighwayPosition(0, -8_000));
        assertTrue(PrivacyText.isMainHighwayPosition(25_000, 25_002));
        assertTrue(PrivacyText.isMainHighwayPosition(-4_000.4, 4_001.1));
    }

    @Test
    void typicalStashIsOffTheHighway() {
        assertFalse(PrivacyText.isMainHighwayPosition(12_345, 8_000));
        assertFalse(PrivacyText.isMainHighwayPosition(-50_000, 12_000));
        assertFalse(PrivacyText.isMainHighwayPosition(100_000, 50_000));
    }

    @Test
    void overworldOrOffServerStashCannotBeOfficialHighwayEvenOnAnAxis() {
        assertFalse(PrivacyText.isOfficialHighwayPosition(true, false, 80_000, 0));
        assertFalse(PrivacyText.isOfficialHighwayPosition(false, true, 80_000, 0));
        assertFalse(PrivacyText.isOfficialHighwayPosition(true, true, 80_000, 12_000));
        assertTrue(PrivacyText.isOfficialHighwayPosition(true, true, 80_000, 0));
    }

    @Test
    void teleportFromHighwayToStashDropsOfficialHighway() {
        assertTrue(PrivacyText.isOfficialHighwayPosition(true, true, 10_000, 0));
        assertFalse(PrivacyText.isOfficialHighwayPosition(true, false, 48_221, 17_304));
        assertFalse(PrivacyText.isOfficialHighwayPosition(true, true, 48_221, 17_304));
    }

    @Test
    void labeledAndDelimitedCoordinatesAreDetectedAndScrubbed() {
        assertTrue(PrivacyText.containsCoordinates("logout at x=48221 y=64 z=-17304"));
        assertTrue(PrivacyText.containsCoordinates("range 48221, 64, -17304"));
        assertTrue(PrivacyText.containsCoordinates("ghost [48221 64 -17304]"));
        assertTrue(PrivacyText.containsCoordinates("at 48221 64 -17304"));
        assertEquals("logout at [coordinates]", PrivacyText.scrubCoordinates("logout at x=48221 y=64 z=-17304"));
        assertEquals("range [coordinates]", PrivacyText.scrubCoordinates("range 48221, 64, -17304"));
    }

    @Test
    void highwayStatsPayloadsAreNotTreatedAsCoordinates() {
        assertFalse(PrivacyText.containsCoordinates(
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:Steve:6b6t.org:15000:500:300:East:1710000000:true"));
        assertFalse(PrivacyText.containsCoordinates(
            "{\"content\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:Steve:+X:500:300:1710000000\"}"));
        assertFalse(PrivacyText.containsCoordinates(
            "Player: Steve , Distance: 15000 , Blocks broken: 500 , Blocks placed: 300"));
    }

    @Test
    void starscriptTemplatesThatWouldPrintPositionAreRejected() {
        assertTrue(PrivacyText.templateCanLeakCoordinates("at {player.pos}"));
        assertTrue(PrivacyText.templateCanLeakCoordinates("{player.x} {player.z}"));
        assertTrue(PrivacyText.templateCanLeakCoordinates("coords {x}"));
        assertFalse(PrivacyText.templateCanLeakCoordinates("Playing {server}"));
        assertFalse(PrivacyText.templateCanLeakCoordinates("{player}"));
        assertFalse(PrivacyText.templateCanLeakCoordinates("{server.player_count} players online"));
    }
}
