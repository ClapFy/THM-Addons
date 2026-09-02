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

class HighwayApiPolicyTest {
    @Test
    void stashSessionCannotSendAfterLeavingTheHighway() {
        assertFalse(HighwayApiPolicy.allowsStatsExport(false, false));
    }

    @Test
    void inProgressPavingOnTheOfficialHighwayCanSendStatus() {
        assertTrue(HighwayApiPolicy.allowsStatsExport(false, true));
    }

    @Test
    void earnedOfficialHighwaySessionCanSendAfterHomeOrReconnect() {
        assertTrue(HighwayApiPolicy.allowsStatsExport(true, false));
        assertTrue(HighwayApiPolicy.allowsStatsExport(true, true));
    }

    @Test
    void oneBrokenTwoPlacedIsARepairNotAStatsSession() {
        assertTrue(HighwayApiPolicy.isRepairSession(2, 1));
        assertTrue(HighwayApiPolicy.isRepairSession(299, 999));
        assertFalse(HighwayApiPolicy.isRepairSession(300, 0));
        assertFalse(HighwayApiPolicy.isRepairSession(0, 1000));
    }

    @Test
    void standingStillIsTooSmallToSend() {
        assertTrue(HighwayApiPolicy.isDistanceTooSmall(0));
        assertTrue(HighwayApiPolicy.isDistanceTooSmall(1));
        assertFalse(HighwayApiPolicy.isDistanceTooSmall(1.1));
        assertFalse(HighwayApiPolicy.isDistanceTooSmall(50_000));
    }

    @Test
    void jsonContentMatchesMainRepoContentEnvelope() {
        String body = HighwayApiPolicy.jsonContent(
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:Steve:6b6t.org:15000:500:300:East:1710000000:true");
        assertEquals(
            "{\"content\": \"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:Steve:6b6t.org:15000:500:300:East:1710000000:true\"}",
            body);
        assertFalse(PrivacyText.containsCoordinates(body));
    }

    @Test
    void jsonContentEscapesQuotesWithoutTreatingStatsAsCoordinates() {
        String body = HighwayApiPolicy.jsonContent("token:\"quoted\":axis:1:2:3");
        assertEquals("{\"content\": \"token:\\\"quoted\\\":axis:1:2:3\"}", body);
        assertFalse(PrivacyText.containsCoordinates(body));
    }
}
