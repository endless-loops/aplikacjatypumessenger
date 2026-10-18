const fs = require("fs");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");
const {
  doc, getDoc, setDoc, updateDoc, deleteDoc, collection, query, where, orderBy, getDocs,
  arrayRemove, arrayUnion, writeBatch, runTransaction,
} = require("firebase/firestore");

let env;
const db = (uid) => env.authenticatedContext(uid).firestore();

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-messenger",
    firestore: { host: "127.0.0.1", port: 8085, rules: fs.readFileSync(require("path").join(__dirname, "..", "firestore.rules"), "utf8") },
  });
});
after(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const a = ctx.firestore();
    for (const id of ["alice", "bob", "carol", "eve"]) await setDoc(doc(a, "users", id), { id, username: id, status: "offline" });
    await setDoc(doc(a, "chats", "dm"), { id: "dm", participants: ["alice", "bob"], isGroup: false });
    await setDoc(doc(a, "chats", "grp"), { id: "grp", participants: ["alice", "bob", "carol"], isGroup: true, groupAdmin: "alice", groupName: "G" });
    await setDoc(doc(a, "messages", "m1"), { id: "m1", chatId: "dm", senderId: "bob", text: "hej", timestamp: 1, status: "sent", seen: false });
  });
});

describe("users", () => {
  it("signed-in user can list users", () => assertSucceeds(getDocs(collection(db("alice"), "users"))));
  it("can update own presence", () => assertSucceeds(updateDoc(doc(db("alice"), "users", "alice"), { status: "online", lastSeen: 2 })));
  it("cannot update someone else", () => assertFails(updateDoc(doc(db("alice"), "users", "bob"), { status: "online" })));
});

describe("chats", () => {
  it("chat list query (array-contains) works", () =>
    assertSucceeds(getDocs(query(collection(db("alice"), "chats"), where("participants", "array-contains", "alice")))));
  it("outsider cannot read chat", () => assertFails(getDoc(doc(db("eve"), "chats", "dm"))));
  it("can create private chat with self", () =>
    assertSucceeds(setDoc(doc(db("alice"), "chats", "new"), { id: "new", participants: ["alice", "carol"], isGroup: false })));
  it("cannot create group with someone else as admin", () =>
    assertFails(setDoc(doc(db("alice"), "chats", "g2"), { participants: ["alice", "bob"], isGroup: true, groupAdmin: "bob" })));
  it("admin adds members", () => assertSucceeds(updateDoc(doc(db("alice"), "chats", "grp"), { participants: arrayUnion("eve") })));
  it("member cannot add members", () => assertFails(updateDoc(doc(db("bob"), "chats", "grp"), { participants: arrayUnion("eve") })));
  it("member can leave", () => assertSucceeds(updateDoc(doc(db("bob"), "chats", "grp"), { participants: arrayRemove("bob") })));
  it("member cannot remove others", () => assertFails(updateDoc(doc(db("bob"), "chats", "grp"), { participants: arrayRemove("carol") })));
  it("admin leaves and hands over admin", () =>
    assertSucceeds(updateDoc(doc(db("alice"), "chats", "grp"), { participants: arrayRemove("alice"), groupAdmin: "bob" })));
  it("member cannot become admin", () => assertFails(updateDoc(doc(db("bob"), "chats", "grp"), { groupAdmin: "bob" })));
  it("admin deletes group", () => assertSucceeds(deleteDoc(doc(db("alice"), "chats", "grp"))));
  it("member cannot delete group", () => assertFails(deleteDoc(doc(db("bob"), "chats", "grp"))));
});

describe("messages", () => {
  const msgQuery = (uid, chatId) =>
    query(collection(db(uid), "messages"), where("chatId", "==", chatId), orderBy("timestamp", "asc"));

  it("participant can run the chat messages query", () => assertSucceeds(getDocs(msgQuery("alice", "dm"))));
  it("outsider cannot run the chat messages query", () => assertFails(getDocs(msgQuery("eve", "dm"))));
  it("unfiltered message query is rejected", () => assertFails(getDocs(collection(db("alice"), "messages"))));

  it("send = batch(message + chat lastMessage)", () => {
    const d = db("alice");
    const b = writeBatch(d);
    b.set(doc(d, "messages", "m2"), { id: "m2", chatId: "dm", senderId: "alice", text: "yo", timestamp: 2, status: "sent" });
    b.update(doc(d, "chats", "dm"), { lastMessage: { text: "yo", senderId: "alice", timestamp: 2 }, lastMessageTime: 2 });
    return assertSucceeds(b.commit());
  });
  it("cannot send as someone else", () =>
    assertFails(setDoc(doc(db("alice"), "messages", "m3"), { chatId: "dm", senderId: "bob", text: "x", timestamp: 3 })));
  it("outsider cannot send", () =>
    assertFails(setDoc(doc(db("eve"), "messages", "m4"), { chatId: "dm", senderId: "eve", text: "x", timestamp: 3 })));
  it("recipient marks as read", () =>
    assertSucceeds(updateDoc(doc(db("alice"), "messages", "m1"), { status: "read", seen: true, readAt: 5 })));
  it("cannot edit text of a message", () => assertFails(updateDoc(doc(db("alice"), "messages", "m1"), { text: "zmienione" })));
  it("markAsDelivered transaction", () => {
    const d = db("alice");
    return assertSucceeds(runTransaction(d, async (t) => {
      const ref = doc(d, "messages", "m1");
      const snap = await t.get(ref);
      if (snap.get("status") === "sent") t.update(ref, { status: "delivered", deliveredAt: 4 });
    }));
  });
});
