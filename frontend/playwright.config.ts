import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir:'./e2e',fullyParallel:false,workers:1,timeout:60000,
  expect:{timeout:15000},reporter:'list',outputDir:'test-results/playwright',
  use:{baseURL:'http://127.0.0.1:5183',trace:'retain-on-failure',
    ...(process.env.PLAYWRIGHT_CHANNEL?{channel:process.env.PLAYWRIGHT_CHANNEL}:{}),
  },
  webServer:[
    {command:'node e2e/start-backend.mjs',url:'http://127.0.0.1:8893/actuator/health',timeout:180000,reuseExistingServer:false,stdout:'pipe',stderr:'pipe'},
    {command:'npm run dev -- --host 127.0.0.1 --port 5183 --strictPort',url:'http://127.0.0.1:5183',
      env:{VITE_DEV_SPRING_ORIGIN:'http://127.0.0.1:8893'},timeout:60000,reuseExistingServer:false},
  ],
});
