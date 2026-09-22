package me.pauleff.converter.mapping;

import me.pauleff.common.exceptions.MappingException;
import me.pauleff.converter.ConversionTarget;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MappingTableTest
{
    private static final String TESTER_OFFLINE = "f3d28cb0-7225-3cb1-baeb-2dadd2be89ae";
    private static final String ALEX_OFFLINE = "36532b5e-c442-3dbb-a24c-c7e55d0f979a";
    private static final String TESTER_ONLINE = "8a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
    private static final String ALEX_ONLINE = "1b2c3d4e-5f6a-4b7c-9d8e-0f1a2b3c4d5e";

    @TempDir
    Path tempDir;

    private Path writeMapping(String json) throws IOException
    {
        return Files.writeString(tempDir.resolve("mapping.json"), json);
    }

    private static String entry(String name, String offline, String online)
    {
        return "{\"name\":\"%s\",\"offline\":\"%s\",\"online\":\"%s\"}".formatted(name, offline, online);
    }

    private static String mapping(String direction, String... entries)
    {
        return "{\"version\":1,\"direction\":\"%s\",\"entries\":[%s]}"
                .formatted(direction, String.join(",", entries));
    }

    @Nested
    class Load
    {
        @Test
        void orientsOfflineToThirdparty_whenDirectionIsOfflineToThirdparty() throws Exception
        {
            Path path = writeMapping(mapping(
                    "offline-to-thirdparty",
                    entry("Tester", TESTER_OFFLINE, TESTER_ONLINE)));

            MappingTable table = MappingTable.load(path);

            assertEquals(ConversionTarget.ONLINE, table.direction());
            assertEquals(1, table.uuidMap().size());
            assertEquals(UUID.fromString(TESTER_ONLINE), table.uuidMap().get(UUID.fromString(TESTER_OFFLINE)));
        }

        @Test
        void orientsThirdpartyToOffline_whenDirectionIsThirdpartyToOffline() throws Exception
        {
            Path path = writeMapping(mapping(
                    "thirdparty-to-offline",
                    entry("Tester", TESTER_OFFLINE, TESTER_ONLINE)));

            MappingTable table = MappingTable.load(path);

            assertEquals(ConversionTarget.OFFLINE, table.direction());
            assertEquals(UUID.fromString(TESTER_OFFLINE), table.uuidMap().get(UUID.fromString(TESTER_ONLINE)));
        }

        @Test
        void keepsEveryHistoryEntry_whenOneAccountHasSeveralNames() throws Exception
        {
            Path path = writeMapping(mapping(
                    "offline-to-thirdparty",
                    entry("Tester", TESTER_OFFLINE, TESTER_ONLINE),
                    entry("Alex", ALEX_OFFLINE, TESTER_ONLINE)));

            MappingTable table = MappingTable.load(path);

            assertEquals(2, table.uuidMap().size());
            assertEquals(UUID.fromString(TESTER_ONLINE), table.uuidMap().get(UUID.fromString(ALEX_OFFLINE)));
        }

        @Test
        void parsesUuids_whenTheyOmitHyphens() throws Exception
        {
            Path path = writeMapping(mapping(
                    "offline-to-thirdparty",
                    entry("Tester",
                            TESTER_OFFLINE.replace("-", ""),
                            TESTER_ONLINE.replace("-", ""))));

            MappingTable table = MappingTable.load(path);

            assertEquals(UUID.fromString(TESTER_ONLINE), table.uuidMap().get(UUID.fromString(TESTER_OFFLINE)));
        }

        @Test
        void throws_whenFileIsMissing()
        {
            Path missing = tempDir.resolve("absent-mapping.json");

            assertThrows(IOException.class, () -> MappingTable.load(missing));
        }

        @Test
        void throws_whenJsonIsMalformed() throws IOException
        {
            Path path = writeMapping("{not json");

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenVersionIsUnsupported() throws IOException
        {
            Path path = writeMapping("""
                    {"version":2,"direction":"offline-to-thirdparty","entries":[
                      {"name":"Tester","offline":"%s","online":"%s"}]}
                    """.formatted(TESTER_OFFLINE, TESTER_ONLINE));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenDirectionIsUnknown() throws IOException
        {
            Path path = writeMapping(mapping("sideways",
                    entry("Tester", TESTER_OFFLINE, TESTER_ONLINE)));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenDirectionIsMissing() throws IOException
        {
            Path path = writeMapping("""
                    {"version":1,"entries":[{"name":"Tester","offline":"%s","online":"%s"}]}
                    """.formatted(TESTER_OFFLINE, TESTER_ONLINE));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenEntriesAreMissing() throws IOException
        {
            Path path = writeMapping("""
                    {"version":1,"direction":"offline-to-thirdparty"}
                    """);

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenEntriesAreEmpty() throws IOException
        {
            Path path = writeMapping(mapping("offline-to-thirdparty"));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenEntryHasNoName() throws IOException
        {
            Path path = writeMapping(mapping("offline-to-thirdparty",
                    "{\"offline\":\"%s\",\"online\":\"%s\"}".formatted(TESTER_OFFLINE, TESTER_ONLINE)));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenOfflineUuidIsInvalid() throws IOException
        {
            Path path = writeMapping(mapping("offline-to-thirdparty",
                    entry("Tester", "not-a-uuid", TESTER_ONLINE)));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenOfflineUuidDoesNotMatchTheName() throws IOException
        {
            Path path = writeMapping(mapping("offline-to-thirdparty",
                    entry("Tester", ALEX_OFFLINE, TESTER_ONLINE)));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenOfflineUuidIsDuplicated() throws IOException
        {
            Path path = writeMapping(mapping("offline-to-thirdparty",
                    entry("Tester", TESTER_OFFLINE, TESTER_ONLINE),
                    entry("Tester", TESTER_OFFLINE, ALEX_ONLINE)));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }

        @Test
        void throws_whenThirdpartyUuidIsDuplicated_forOfflineDirection() throws IOException
        {
            Path path = writeMapping(mapping("thirdparty-to-offline",
                    entry("Tester", TESTER_OFFLINE, TESTER_ONLINE),
                    entry("Alex", ALEX_OFFLINE, TESTER_ONLINE)));

            assertThrows(MappingException.class, () -> MappingTable.load(path));
        }
    }
}
