import { createApp } from "vue";
import App from "./app/App.vue";
import router from "./router";
import "@fontsource/fusion-pixel-12px-proportional-sc";
import "@fontsource/inter/latin-400.css";
import "@fontsource/inter/latin-500.css";
import "@fontsource/inter/latin-600.css";
import "./shared/design/tokens.css";
import "./shared/design/pixel-font.css";
import { installLiquidGlass } from "./shared/design/liquid-glass";
import { initializeTheme } from "./shared/design/theme";
import { initializeLocale } from "./shared/i18n/locale";

initializeTheme();
initializeLocale();
createApp(App).use(router).mount("#app");
installLiquidGlass();
