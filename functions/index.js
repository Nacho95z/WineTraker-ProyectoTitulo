const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { initializeApp } = require("firebase-admin/app");

initializeApp();

exports.calculateOptimalConsumption = onDocumentCreated(
  "descriptions/{userId}/wineDescriptions/{wineId}",
  async (event) => {
    const wineData = event.data.data();

    const variety = wineData.variety?.toLowerCase();
    const vintage = parseInt(wineData.vintage, 10);
    const currentYear = new Date().getFullYear();

    let optimalStart = 0;
    let optimalEnd = 0;

    // Lógica para determinar el rango óptimo de consumo
    if (variety.includes("pinot noir") || variety.includes("gamay")) {
      optimalStart = vintage + 2;
      optimalEnd = vintage + 5;
    } else if (variety.includes("merlot") || variety.includes("tempranillo")) {
      optimalStart = vintage + 5;
      optimalEnd = vintage + 10;
    } else if (variety.includes("cabernet sauvignon") || variety.includes("syrah")) {
      optimalStart = vintage + 10;
      optimalEnd = vintage + 20;
    } else if (variety.includes("sauvignon blanc")) {
      optimalStart = vintage + 1;
      optimalEnd = vintage + 3;
    } else if (variety.includes("chardonnay") || variety.includes("viognier")) {
      optimalStart = vintage + 5;
      optimalEnd = vintage + 8;
    } else if (variety.includes("sauternes") || variety.includes("porto")) {
      optimalStart = vintage + 20;
      optimalEnd = vintage + 50;
    } else {
      optimalStart = vintage + 2;
      optimalEnd = vintage + 5;
    }

    // Enviar notificación push si el vino está en su momento óptimo de consumo
    if (currentYear >= optimalStart && currentYear <= optimalEnd) {
      const userId = event.params.userId;

      await getMessaging().sendToTopic(userId, {
        notification: {
          title: "¡Tu vino está listo para disfrutar!",
          body: `El vino ${wineData.wineName} (${wineData.vintage}) está en su momento óptimo de consumo.`,
        },
      });
    }

    // Actualizar el documento con la información del momento óptimo de consumo
    return event.data.ref.update({
      optimalConsumption: {
        start: optimalStart,
        end: optimalEnd,
      },
    });
  }
);
