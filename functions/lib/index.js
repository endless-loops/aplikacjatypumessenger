"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onNewGroupMessage = exports.onNewPrivateMessage = void 0;
const functions = require("firebase-functions/v2");
const admin = require("firebase-admin");
admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();
// ─── Prywatny czat: nowa wiadomość ──────────────────────────────────────────
exports.onNewPrivateMessage = functions.firestore.onDocumentCreated("messages/{messageId}", async (event) => {
    var _a, _b, _c, _d, _e;
    const message = (_a = event.data) === null || _a === void 0 ? void 0 : _a.data();
    if (!message)
        return;
    // Pomijamy wiadomości grupowe
    if (message.isGroupMessage === true)
        return;
    const senderId = message.senderId;
    const receiverId = message.receiverId;
    const chatId = message.chatId;
    const text = (_b = message.text) !== null && _b !== void 0 ? _b : "";
    if (!receiverId || !senderId || !chatId)
        return;
    // Pobierz nadawcę i odbiorcę równolegle
    const [senderDoc, receiverDoc] = await Promise.all([
        db.collection("users").doc(senderId).get(),
        db.collection("users").doc(receiverId).get(),
    ]);
    const fcmToken = (_c = receiverDoc.data()) === null || _c === void 0 ? void 0 : _c.fcmToken;
    if (!fcmToken)
        return;
    const senderName = (_e = (_d = senderDoc.data()) === null || _d === void 0 ? void 0 : _d.name) !== null && _e !== void 0 ? _e : "Nowa wiadomość";
    await messaging.send({
        token: fcmToken,
        notification: {
            title: senderName,
            body: text.length > 100 ? text.substring(0, 97) + "..." : text,
        },
        data: {
            chatId,
            senderId,
            senderName,
            body: text,
            type: "private",
        },
        android: {
            priority: "high",
            notification: {
                channelId: "chat_notifications",
                clickAction: "OPEN_CHAT",
            },
        },
    });
});
// ─── Czat grupowy: nowa wiadomość ───────────────────────────────────────────
exports.onNewGroupMessage = functions.firestore.onDocumentCreated("messages/{messageId}", async (event) => {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const message = (_a = event.data) === null || _a === void 0 ? void 0 : _a.data();
    if (!message)
        return;
    // Obsługujemy tylko wiadomości grupowe
    if (message.isGroupMessage !== true)
        return;
    const senderId = message.senderId;
    const groupId = message.chatId;
    const text = (_b = message.text) !== null && _b !== void 0 ? _b : "";
    if (!senderId || !groupId)
        return;
    // Pobierz grupę i nadawcę równolegle
    const [groupDoc, senderDoc] = await Promise.all([
        db.collection("chats").doc(groupId).get(),
        db.collection("users").doc(senderId).get(),
    ]);
    if (!groupDoc.exists)
        return;
    const members = (_d = (_c = groupDoc.data()) === null || _c === void 0 ? void 0 : _c.members) !== null && _d !== void 0 ? _d : [];
    const groupName = (_f = (_e = groupDoc.data()) === null || _e === void 0 ? void 0 : _e.name) !== null && _f !== void 0 ? _f : "Czat grupowy";
    const senderName = (_h = (_g = senderDoc.data()) === null || _g === void 0 ? void 0 : _g.name) !== null && _h !== void 0 ? _h : "Ktoś";
    // Zbierz tokeny wszystkich członków oprócz nadawcy
    const memberDocs = await Promise.all(members
        .filter((uid) => uid !== senderId)
        .map((uid) => db.collection("users").doc(uid).get()));
    const tokens = memberDocs
        .map((doc) => { var _a; return (_a = doc.data()) === null || _a === void 0 ? void 0 : _a.fcmToken; })
        .filter((token) => !!token);
    if (tokens.length === 0)
        return;
    // Wysyłamy do wszystkich tokenów (max 500 naraz — FCM limit)
    const chunks = chunkArray(tokens, 500);
    await Promise.all(chunks.map((chunk) => messaging.sendEachForMulticast({
        tokens: chunk,
        notification: {
            title: groupName,
            body: `${senderName}: ${text.length > 80 ? text.substring(0, 77) + "..." : text}`,
        },
        data: {
            chatId: groupId,
            senderId,
            senderName,
            groupName,
            body: text,
            type: "group",
        },
        android: {
            priority: "high",
            notification: {
                channelId: "chat_notifications",
                clickAction: "OPEN_GROUP_CHAT",
            },
        },
    })));
});
function chunkArray(arr, size) {
    const chunks = [];
    for (let i = 0; i < arr.length; i += size) {
        chunks.push(arr.slice(i, i + size));
    }
    return chunks;
}
//# sourceMappingURL=index.js.map