// Cloud Functions aplikacji - kod uruchamiany po stronie serwera Firebase.
// Obie funkcje reagują na zmiany w Firestore (wyzwalacze), aplikacja nie wywołuje ich bezpośrednio.
// Działają z uprawnieniami administratora (Admin SDK), więc reguły bezpieczeństwa ich nie ograniczają.
const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

// Bez parametrów - w środowisku Cloud Functions konfiguracja projektu jest dostępna automatycznie
initializeApp();
const db = getFirestore();

// FCM przyjmuje maksymalnie ok. 4 KB danych, więc długie wiadomości przycinamy
const MAX_BODY_LENGTH = 200;

function truncate(text) {
  const chars = Array.from(text || ""); // bez rozcinania emoji i znaków spoza BMP
  return chars.length > MAX_BODY_LENGTH ? chars.slice(0, MAX_BODY_LENGTH - 1).join("") + "…" : chars.join("");
}

// Błędy oznaczające, że token już nie działa (np. aplikację odinstalowano)
const INVALID_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

// Wysyła powiadomienie FCM do wszystkich uczestników czatu poza nadawcą.
// Uruchamia się przy każdym nowym dokumencie w kolekcji `messages`.
exports.sendMessageNotification = onDocumentCreated("messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message || !message.chatId) return;

  // Uczestników bierzemy z dokumentu czatu, a nie z wiadomości - aktualny skład grupy
  const chatSnap = await db.collection("chats").doc(message.chatId).get();
  if (!chatSnap.exists) return;
  const chat = chatSnap.data();

  const recipientIds = (chat.participants || []).filter((id) => id !== message.senderId);
  if (recipientIds.length === 0) return;

  // getAll pobiera wszystkie profile jednym zapytaniem: nadawcę (dla nazwy) i odbiorców (dla tokenów)
  const [senderSnap, ...recipientSnaps] = await db.getAll(
    db.collection("users").doc(message.senderId),
    ...recipientIds.map((id) => db.collection("users").doc(id)),
  );

  // Pomijamy osoby bez tokenu, np. wylogowane (aplikacja czyści token przy wylogowaniu)
  const recipients = recipientSnaps
    .map((snap) => ({ ref: snap.ref, token: snap.get("fcmToken") }))
    .filter((r) => typeof r.token === "string" && r.token.length > 0);
  if (recipients.length === 0) return;

  const senderName = senderSnap.get("username") || "Nowa wiadomość";
  const isGroup = chat.isGroup === true;

  // Tylko payload "data", żeby aplikacja sama decydowała o wyświetleniu (np. nie pokazuje
  // powiadomienia, gdy użytkownik ma otwarty ten czat).
  // Jedno wywołanie dla wszystkich odbiorców; odpowiedzi wracają w tej samej kolejności co tokeny
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
    // Wysoki priorytet - powiadomienie ma dojść od razu, także gdy telefon jest uśpiony
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

  // Widoczne w logach funkcji w konsoli Firebase
  logger.info(`Wysłano ${response.successCount}/${recipients.length} powiadomień`, {
    chatId: message.chatId,
  });
});

// Po usunięciu czatu usuwa jego wiadomości (klient nie może usuwać cudzych wiadomości).
// Firestore nie usuwa powiązanych dokumentów automatycznie, dlatego robi to ta funkcja.
exports.deleteChatMessages = onDocumentDeleted("chats/{chatId}", async (event) => {
  // BulkWriter grupuje usuwanie i sam ponawia nieudane operacje - przydaje się przy długich rozmowach
  const writer = db.bulkWriter();
  const snapshot = await db.collection("messages").where("chatId", "==", event.params.chatId).get();
  snapshot.docs.forEach((doc) => writer.delete(doc.ref));
  await writer.close();
  logger.info(`Usunięto ${snapshot.size} wiadomości czatu ${event.params.chatId}`);
});
