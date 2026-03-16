import * as functions from "firebase-functions/v2";
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();
const messaging = admin.messaging();

// ─── Prywatny czat: nowa wiadomość ──────────────────────────────────────────
export const onNewPrivateMessage = functions.firestore.onDocumentCreated(
  "messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    // Pomijamy wiadomości grupowe
    if (message.isGroupMessage === true) return;

    const senderId: string = message.senderId;
    const receiverId: string = message.receiverId;
    const chatId: string = message.chatId;
    const text: string = message.text ?? "";

    if (!receiverId || !senderId || !chatId) return;

    // Pobierz nadawcę i odbiorcę równolegle
    const [senderDoc, receiverDoc] = await Promise.all([
      db.collection("users").doc(senderId).get(),
      db.collection("users").doc(receiverId).get(),
    ]);

    const fcmToken: string | undefined = receiverDoc.data()?.fcmToken;
    if (!fcmToken) return;

    const senderName: string = senderDoc.data()?.name ?? "Nowa wiadomość";

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
  }
);

// ─── Czat grupowy: nowa wiadomość ───────────────────────────────────────────
export const onNewGroupMessage = functions.firestore.onDocumentCreated(
  "messages/{messageId}",
  async (event) => {
    const message = event.data?.data();
    if (!message) return;

    // Obsługujemy tylko wiadomości grupowe
    if (message.isGroupMessage !== true) return;

    const senderId: string = message.senderId;
    const groupId: string = message.chatId;
    const text: string = message.text ?? "";

    if (!senderId || !groupId) return;

    // Pobierz grupę i nadawcę równolegle
    const [groupDoc, senderDoc] = await Promise.all([
      db.collection("chats").doc(groupId).get(),
      db.collection("users").doc(senderId).get(),
    ]);

    if (!groupDoc.exists) return;

    const members: string[] = groupDoc.data()?.members ?? [];
    const groupName: string = groupDoc.data()?.name ?? "Czat grupowy";
    const senderName: string = senderDoc.data()?.name ?? "Ktoś";

    // Zbierz tokeny wszystkich członków oprócz nadawcy
    const memberDocs = await Promise.all(
      members
        .filter((uid) => uid !== senderId)
        .map((uid) => db.collection("users").doc(uid).get())
    );

    const tokens: string[] = memberDocs
      .map((doc) => doc.data()?.fcmToken as string | undefined)
      .filter((token): token is string => !!token);

    if (tokens.length === 0) return;

    // Wysyłamy do wszystkich tokenów (max 500 naraz — FCM limit)
    const chunks = chunkArray(tokens, 500);
    await Promise.all(
      chunks.map((chunk) =>
        messaging.sendEachForMulticast({
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
        })
      )
    );
  }
);

function chunkArray<T>(arr: T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let i = 0; i < arr.length; i += size) {
    chunks.push(arr.slice(i, i + size));
  }
  return chunks;
}
