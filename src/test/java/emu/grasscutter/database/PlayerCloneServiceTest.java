package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class PlayerCloneServiceTest {
    @Test
    void stringIdsRemainStringsAndAreFreshForEachClone() {
        var source =
                new Document("_id", new ObjectId().toHexString())
                        .append("ownerUid", 10001L)
                        .append("level", 10)
                        .append("sceneMap", new Document("1", new Document("comfortValue", 1234)));
        var original = source.toJson();

        var first = PlayerCloneService.cloneOwnedDocument(source, "ownerUid", 900101);
        var second = PlayerCloneService.cloneOwnedDocument(source, "ownerUid", 900102);

        assertInstanceOf(String.class, first.get("_id"));
        assertInstanceOf(String.class, second.get("_id"));
        assertNotEquals(source.get("_id"), first.get("_id"));
        assertNotEquals(source.get("_id"), second.get("_id"));
        assertNotEquals(first.get("_id"), second.get("_id"));
        assertEquals(900101, first.get("ownerUid"));
        assertEquals(900102, second.get("ownerUid"));
        assertEquals(source.get("level"), first.get("level"));
        assertEquals(source.get("sceneMap"), first.get("sceneMap"));
        assertEquals(original, source.toJson());
    }

    @Test
    void objectIdsAreLeftForMongoToAllocateWithoutChangingSource() {
        var sourceId = new ObjectId();
        var source = new Document("_id", sourceId).append("ownerId", 10001).append("itemId", 201);
        var original = new Document(source);

        var target = PlayerCloneService.cloneOwnedDocument(source, "ownerId", 900101);

        assertFalse(target.containsKey("_id"));
        assertEquals(900101, target.get("ownerId"));
        assertEquals(201, target.get("itemId"));
        // This is the same collectible codec used by insertOne to allocate missing ObjectIds.
        new DocumentCodec().generateIdIfAbsentFromDocument(target);
        assertInstanceOf(ObjectId.class, target.get("_id"));
        assertNotEquals(sourceId, target.get("_id"));
        assertEquals(original, source);
    }
}
