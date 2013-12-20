package brooklyn.entity.nosql.mongodb;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;
import com.sun.org.apache.regexp.internal.RE;

import org.bson.BSONObject;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

public class ReplicaSetConfigTest {

    // true if object has key "votes" that is > 1
    static Predicate<BasicBSONObject> IS_VOTING_MEMBER = new Predicate<BasicBSONObject>() {
        @Override public boolean apply(@Nullable BasicBSONObject input) {
            return input != null && input.containsField("votes") && input.getInt("votes") > 0;
        }
    };

    private BasicBSONObject makeSetMember(Integer id, String host) {
        return new BasicBSONObject(ImmutableMap.of("_id", id, "host", host));
    }

    private BasicBSONObject makeSetConfig(String id, Integer version, BasicBSONObject... members) {
        BasicBSONList memberList = new BasicBSONList();
        memberList.addAll(Arrays.asList(members));
        return new BasicBSONObject(ImmutableMap.of("_id", id, "version", version, "members", memberList));
    }

    private BasicBSONObject makeSetWithNMembers(int n) {
        ReplicaSetConfig setConfig = ReplicaSetConfig.builder("replica-set-name");
        for (int i = 0; i < n; i++) {
            setConfig.member("host-"+i, i, i);
        }
        return setConfig.build();
    }

    private Collection<BasicBSONObject> votingMembersOfSet(BasicBSONObject config) {
        BasicBSONList membersObject = BasicBSONList.class.cast(config.get("members"));
        List<BasicBSONObject> members = Lists.newArrayList();
        for (Object object : membersObject) members.add(BasicBSONObject.class.cast(object));
        return FluentIterable.from(members).filter(IS_VOTING_MEMBER).toList();
    }

    private Collection<BasicBSONObject> nonVotingMembersOfSet(BasicBSONObject config) {
        BasicBSONList membersObject = BasicBSONList.class.cast(config.get("members"));
        List<BasicBSONObject> members = Lists.newArrayList();
        for (Object object : membersObject) members.add(BasicBSONObject.class.cast(object));
        return FluentIterable.from(members).filter(Predicates.not(IS_VOTING_MEMBER)).toList();
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
        BasicBSONObject config = makeSetConfig("replica-set-name", version, makeSetMember(33, "example.com:7777"));

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
        int version = 44;
        BasicBSONObject config = makeSetConfig("replica-set-name", version,
                makeSetMember(33, "example.com:7777"),
                makeSetMember(34, "example.com:7778"));

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
        BasicBSONObject config = makeSetConfig("replica-set-name", 1,
                makeSetMember(33, "example.com:7777"),
                makeSetMember(34, "example.com:7778"));

        BasicBSONList members = (BasicBSONList) config.get("members");
        assertEquals(members.size(), 2);

        BasicBSONObject altered = ReplicaSetConfig.fromExistingConfig(config)
                .remove("foo", 99)
                .build();

        members = (BasicBSONList) altered.get("members");
        assertEquals(members.size(), 2);
    }

    @Test
    public void testSetOfFourMembersHasThreeVoters() {
        BasicBSONObject config = makeSetWithNMembers(4);
        assertEquals(votingMembersOfSet(config).size(), 3, "Expected three voters in set with four members");
        assertEquals(nonVotingMembersOfSet(config).size(), 1, "Expected one non-voter in set with four members");
    }

    @Test
    public void testFourthServerOfFourIsGivenVoteWhenAnotherServerIsRemoved() {
        BasicBSONObject config = makeSetWithNMembers(4);
        BasicBSONObject toRemove = votingMembersOfSet(config).iterator().next();
        HostAndPort removedHostAndPort = HostAndPort.fromString(toRemove.getString("host"));

        BasicBSONObject updated = ReplicaSetConfig.fromExistingConfig(config)
                .remove(HostAndPort.fromString(toRemove.getString("host")))
                .build();

        assertEquals(votingMembersOfSet(updated).size(), 3);
        assertTrue(nonVotingMembersOfSet(updated).isEmpty());

        BasicBSONList newMembers = BasicBSONList.class.cast(updated.get("members"));
        for (Object object : newMembers) {
            BasicBSONObject member = BasicBSONObject.class.cast(object);
            HostAndPort memberHostAndPort = HostAndPort.fromString(member.getString("host"));
            assertNotEquals(memberHostAndPort, removedHostAndPort);
        }
    }

    @Test
    public void testMaximumNumberOfVotersIsLimited() {
        BasicBSONObject config = makeSetWithNMembers(ReplicaSetConfig.MAXIMUM_REPLICA_SET_SIZE);
        int voters = ReplicaSetConfig.MAXIMUM_VOTING_MEMBERS;
        int nonVoters = ReplicaSetConfig.MAXIMUM_REPLICA_SET_SIZE - voters;
        assertEquals(votingMembersOfSet(config).size(), voters, "Expected number of voters in max-size set to be " + voters);
        assertEquals(nonVotingMembersOfSet(config).size(), nonVoters, "Expected number of non-voters in max-size set to be " + nonVoters);
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testMoreMembersThanMaximumAllowsRejected() {
        makeSetWithNMembers(ReplicaSetConfig.MAXIMUM_REPLICA_SET_SIZE + 1);
    }
}
