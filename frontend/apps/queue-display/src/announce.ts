import { spokenNumber } from "./board";

/** Chime and spoken announcement for numbers that just became ready. Browsers allow audio only after a first tap. */
export function announce(numbers: string[], opts: { sound: boolean; voice: boolean; locale: "ro" | "en" }) {
  if (opts.sound) chime();
  if (!opts.voice || !("speechSynthesis" in window)) return;
  numbers.forEach((n, i) => {
    const text = opts.locale === "en" ? `Order ${spokenNumber(n)} is ready` : `Comanda ${spokenNumber(n)} este gata`;
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = opts.locale === "en" ? "en-GB" : "ro-RO";
    utterance.rate = 0.95;
    setTimeout(() => window.speechSynthesis.speak(utterance), 900 + i * 2500);
  });
}

let ctx: AudioContext | undefined;

/** Unlocks audio on the first tap (autoplay rules) and plays a short confirmation. */
export function unlockAudio() {
  ctx ??= new AudioContext();
  void ctx.resume();
  chime();
}

function chime() {
  try {
    ctx ??= new AudioContext();
    [523, 659, 784].forEach((freq, i) => {
      const osc = ctx!.createOscillator();
      const gain = ctx!.createGain();
      osc.type = "sine";
      osc.frequency.value = freq;
      const t = ctx!.currentTime + i * 0.14;
      gain.gain.setValueAtTime(0.0001, t);
      gain.gain.exponentialRampToValueAtTime(0.4, t + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.5);
      osc.connect(gain).connect(ctx!.destination);
      osc.start(t);
      osc.stop(t + 0.55);
    });
  } catch {
    /* audio blocked until the first tap */
  }
}
