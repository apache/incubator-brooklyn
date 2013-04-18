package brooklyn.entity.nosql.mongodb;

import com.google.common.collect.ImmutableMap;
import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ReplicaSetConfigTest {

    private BasicBSONObject makeSetMember(Integer id, String host) {
        return new BasicBSONObject(ImmutableMap.of("_id", id, "host", host));
    }

    @Test
    public void testCreateFromScratch() {
        BasicBSONObject config = ReplicaSetConfig.builder("rs")
            .member("host-a", 12345, 1)
            .member("host-b", 54321, 2)
            .build();
        assertEquals(config.get("_id"), "rs");
        assertEquals(config.getInt("version"), 1);
        assertTrue(config.get("members") instanceof BasicBSONList);
        BasicBSONList members = (BasicBSONList) config.get("members");
        assertEquals(members.size(), 2);
    }

    @Test
    public void testCreateFromExistingConfig() {
        // Replica set of one member
        int version = 44;
        BasicBSONObject initialMember = makeSetMember(33, "example.com:7777");
        BasicBSONList initialMembers = new BasicBSONList();
        initialMembers.add(initialMember);
        BasicBSONObject config = new BasicBSONObject(
                ImmutableMap.of("_id", "replica-set-name", "version", version, "members", initialMembers));

        // Use existing set to add two more members
        BasicBSONObject newConfig = ReplicaSetConfig.fromExistingConfig(config)
            .member("foo", 8888, 34)
            .member("bar", 9999, 35)
            .build();

        assertEquals(newConfig.get("_id"), "replica-set-name");
        assertEquals(newConfig.get("version"), version + 1);
        BasicBSONList members = (BasicBSONList) newConfig.get("members");
        assertEquals(members.size(), 3);

        BSONObject original = (BSONObject) members.get(0);
        assertEquals(original.get("_id"), 33);
        assertEquals(original.get("host"), "example.com:7777");

        BSONObject second = (BSONObject) members.get(1);
        assertEquals(second.get("_id"), 34);
        assertEquals(second.get("host"), "foo:8888");

        BSONObject third = (BSONObject) members.get(2);
        assertEquals(third.get("_id"), 35);
        assertEquals(third.get("host"), "bar:9999");
    }

    @Test
    public void testRemoveMember() {
        // Replica set of two members
        int version = 44;
        BasicBSONObject memberA = makeSetMember(33, "example.com:7777");
        BasicBSONObject memberB = makeSetMember(34, "example.com:7778");
        BasicBSONList initialMembers = new BasicBSONList();
        initialMembers.add(memberA);
        initialMembers.add(memberB);
        BasicBSONObject config = new BasicBSONObject(
                ImmutableMap.of("_id", "replica-set-name", "version", version, "members", initialMembers));

        // Use existing set to add two more members
        BasicBSONObject newConfig = ReplicaSetConfig.fromExistingConfig(config)
            .remove("example.com", 7777)
            .build();

        assertEquals(newConfig.get("version"), version + 1);
        BasicBSONList members = (BasicBSONList) newConfig.get("members");
        assertEquals(members.size(), 1);
        assertEquals(BSONObject.class.cast(members.get(0)).get("host"), "example.com:7778");

        newConfig = ReplicaSetConfig.fromExistingConfig(newConfig)
            .remove("example.com", 7778)
            .build();

        members = (BasicBSONList) newConfig.get("members");
        assertTrue(members.isEmpty());
    }

    @Test
    public void testRemoveNonExistentMemberHasNoEffect() {
        // Replica set of two members
        BasicBSONObject memberA = makeSetMember(33, "example.com:7777");
        BasicBSONObject memberB = makeSetMember(34, "example.com:7778");
        BasicBSONList initialMembers = new BasicBSONList();
        initialMembers.add(memberA);
        initialMembers.add(memberB);
        BasicBSONObject config = new BasicBSONObject(
                ImmutableMap.of("_id", "replica-set-name", "version", 1, "members", initialMembers));

        BasicBSONList members = (BasicBSONList) config.get("members");
        assertEquals(members.size(), 2);

        BasicBSONObject altered = ReplicaSetConfig.fromExistingConfig(config)
                .remove("foo", 99)
                .build();

        members = (BasicBSONList) altered.get("members");
        assertEquals(members.size(), 2);
    }

}
