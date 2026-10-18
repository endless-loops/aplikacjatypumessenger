const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();

// FCM przyjmuje maksymalnie ok. 4 KB danych, więc długie wiadomości przycinamy
const MAX_BODY_LENGTH = 200;

function truncate(text) {
  const chars = Array.from(text || ""); // bez rozcinania emoji i znaków spoza BMP
  return chars.length > MAX_BODY_LENGTH ? chars.slice(0, MAX_BODY_LENGTH - 1).join("") + "…" : chars.join("");
}

const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

// Wysyła powiadomienie FCM do wszystkich uczestników czatu poza nadawcą.
exports.sendMessageNotification = onDocumentCreated("messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message || !message.chatId) return;

  const chatSnap = await db.collection("chats").doc(message.chatId).get();
  if (!chatSnap.exists) return;
  const chat = chatSnap.data();

  const recipientIds = (chat.participants || []).filter((id) => id !== message.senderId);
  if (recipientIds.length === 0) return;

  const [senderSnap, ...recipientSnaps] = await db.getAll(
    db.collection("users").doc(message.senderId),
    ...recipientIds.map((id) => db.collection("users").doc(id)),
  );

  const recipients = recipientSnaps
    .map((snap) => ({ ref: snap.ref, token: snap.get("fcmToken") }))
    .filter((r) => typeof r.token === "string" && r.token.length > 0);
  if (recipients.length === 0) return;

  const senderName = senderSnap.get("username") || "Nowa wiadomość";
  const isGroup = chat.isGroup === true;

  // Tylko payload "data", żeby aplikacja sama decydowała o wyświetleniu (np. nie pokazuje
  // powiadomienia, gdy użytkownik ma otwarty ten czat).
  const response = await getMessaging().sendEachForMulticast({
    tokens: recipients.map((r) => r.token),
    data: {
      chatId: message.chatId,
      messageId: event.params.messageId,
      senderId: message.senderId,
      title: isGroup ? (chat.groupName || "Grupa") : senderName,
      body: truncate(isGroup ? `${senderName}: ${message.text || ""}` : message.text),
      isGroup: String(isGroup),
      // Pola czytane przez starsze wersje aplikacji
      senderName,
      groupName: isGroup ? (chat.groupName || "Grupa") : "",
      type: isGroup ? "group" : "private",
    },
    android: { priority: "high" },
  });

  // Usuń nieaktualne tokeny, żeby nie wysyłać na nie kolejnych powiadomień
  const cleanup = [];
  response.responses.forEach((res, i) => {
    if (!res.success && INVALID_TOKEN_CODES.has(res.error?.code)) {
      cleanup.push(recipients[i].ref.update({ fcmToken: "" }));
    }
  });
  await Promise.all(cleanup);

  logger.info(`Wysłano ${response.successCount}/${recipients.length} powiadomień`, {
    chatId: message.chatId,
  });
});

// Po usunięciu czatu usuwa jego wiadomości (klient nie może usuwać cudzych wiadomości).
exports.deleteChatMessages = onDocumentDeleted("chats/{chatId}", async (event) => {
  const writer = db.bulkWriter();
  const snapshot = await db.collection("messages").where("chatId", "==", event.params.chatId).get();
  snapshot.docs.forEach((doc) => writer.delete(doc.ref));
  await writer.close();
  logger.info(`Usunięto ${snapshot.size} wiadomości czatu ${event.params.chatId}`);
});
