/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PublicHostsTest {
    @Test
    void placeholderExampleHostsAreRejectedBeforeDns() {
        assertEquals(PublicHosts.Status.PLACEHOLDER, PublicHosts.classify("your-api.example.com"));
        assertEquals(PublicHosts.Status.PLACEHOLDER, PublicHosts.classify("your-website.example.com"));
        assertEquals(PublicHosts.Status.PLACEHOLDER, PublicHosts.classify("example.org"));
        assertTrue(PublicHosts.isPlaceholderHost("your-api.example.com."));
        assertTrue(PublicHosts.isPlaceholderUrl("https://your-api.example.com/users"));
        assertFalse(PublicHosts.isConfiguredApiUrl("https://your-api.example.com/users"));
        assertFalse(PublicHosts.isConfiguredApiUrl("http://api.highwaymen.cc/"));
        assertFalse(PublicHosts.isConfiguredApiUrl(null));
    }

    @Test
    void officialHttpsApiUrlsCountAsConfiguredWithoutDns() {
        assertTrue(PublicHosts.isConfiguredApiUrl("https://api.highwaymen.cc/"));
        assertTrue(PublicHosts.isConfiguredApiUrl("https://api.highwaymen.cc/users"));
        assertTrue(PublicHosts.isConfiguredApiUrl("https://highwaymen.cc/cape/index"));
        assertFalse(PublicHosts.isPlaceholderUrl("https://api.highwaymen.cc/"));
    }

    @Test
    void loopbackAndRfc1918AddressesArePrivate() throws Exception {
        assertEquals(PublicHosts.Status.PRIVATE, PublicHosts.classify("localhost"));
        assertEquals(PublicHosts.Status.PRIVATE, PublicHosts.classify("127.0.0.1"));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("10.0.0.1")));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("192.168.1.1")));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("172.16.0.1")));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("169.254.1.1")));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("192.0.2.1")));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("192.0.0.8")));
        assertFalse(PublicHosts.isPublicAddress(InetAddress.getByName("0.0.0.0")));
    }

    @Test
    void publicUnicastAndNonTest192Dot0AreAllowed() throws Exception {
        assertTrue(PublicHosts.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(PublicHosts.isPublicAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(PublicHosts.isPublicAddress(InetAddress.getByName("104.21.44.129")));
        // 192.0.1.0/24 is public unicast; the old /16 check would have blocked it
        assertTrue(PublicHosts.isPublicAddress(InetAddress.getByName("192.0.1.1")));
        assertEquals(PublicHosts.Status.PUBLIC, PublicHosts.classify("8.8.8.8"));
    }

    @Test
    void officialApiHostResolvesPublicWhenDnsWorks() {
        PublicHosts.Status status = PublicHosts.classify("api.highwaymen.cc");
        assumeTrue(status != PublicHosts.Status.UNRESOLVED, "DNS unavailable in this environment");
        assertEquals(PublicHosts.Status.PUBLIC, status);
    }

    @Test
    void generatedVaultUsesOfficialHighwaymenEndpoints() {
        assertEquals("https://api.highwaymen.cc/users", GeneratedApiEndpoints.memberHudUrl());
        assertEquals("https://api.highwaymen.cc/", GeneratedApiEndpoints.highwayUrl());
        assertEquals("https://api.highwaymen.cc/", GeneratedApiEndpoints.statusUrl());
        assertEquals("https://api.highwaymen.cc/status", GeneratedApiEndpoints.highwayStatusUrl());
        assertEquals("https://api.highwaymen.cc/cape/", GeneratedApiEndpoints.capeListUrl());
        assertEquals("https://api.highwaymen.cc/cape/", GeneratedApiEndpoints.capePostUrl());
        assertEquals("https://highwaymen.cc/cape/index", GeneratedApiEndpoints.capeIndexUrl());
        assertTrue(PublicHosts.isConfiguredApiUrl(GeneratedApiEndpoints.highwayUrl()));
        assertFalse(PublicHosts.isPlaceholderUrl(GeneratedApiEndpoints.memberHudUrl()));
    }

    @Test
    void secretsExampleBakesOfficialHighwaymenHostsNotPlaceholders() throws Exception {
        Path example = Path.of("secrets.properties.example");
        assumeTrue(Files.isRegularFile(example), "secrets.properties.example missing from cwd " + Path.of(".").toAbsolutePath());
        String text = Files.readString(example, StandardCharsets.UTF_8);
        assertTrue(text.contains("https://api.highwaymen.cc/users"));
        assertTrue(text.contains("https://api.highwaymen.cc/"));
        assertTrue(text.contains("https://api.highwaymen.cc/status"));
        assertTrue(text.contains("https://api.highwaymen.cc/cape/"));
        assertTrue(text.contains("https://highwaymen.cc/cape/index"));
        assertFalse(text.contains("your-api.example.com"));
        assertFalse(text.contains("your-website.example.com"));
    }
}
