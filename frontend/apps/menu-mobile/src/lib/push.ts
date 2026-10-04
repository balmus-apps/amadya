import { registerPushSubscription } from "@amadya/api-client";
import Constants from "expo-constants";
import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import { Platform } from "react-native";

Notifications.setNotificationHandler({
  handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
});

export type PushResult = "registered" | "denied" | "unavailable";

/**
 * Asks for permission and registers this device's Expo push token for one order.
 * Unavailable on simulators, the web, Expo Go on Android and builds without an EAS project id;
 * the order screen then relies on the live stream while the app is open.
 */
export async function enableOrderPush(orderId: string, trackingToken: string, locale: string): Promise<PushResult> {
  if (Platform.OS === "web" || !Device.isDevice) return "unavailable";
  const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) return "unavailable";

  if (Platform.OS === "android") {
    await Notifications.setNotificationChannelAsync("orders", { name: "Orders", importance: Notifications.AndroidImportance.HIGH });
  }
  let { status } = await Notifications.getPermissionsAsync();
  if (status !== "granted") status = (await Notifications.requestPermissionsAsync()).status;
  if (status !== "granted") return "denied";

  try {
    const { data: expoPushToken } = await Notifications.getExpoPushTokenAsync({ projectId });
    const { error } = await registerPushSubscription({
      path: { orderId },
      query: { token: trackingToken },
      body: { expoPushToken, locale: locale === "en" ? "en" : "ro" },
    });
    return error ? "unavailable" : "registered";
  } catch {
    return "unavailable";
  }
}
