import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir:'./tests',workers:1,fullyParallel:false,retries:0,timeout:45000,
  expect:{timeout:10000},reporter:[['line'],['json',{outputFile:'test-results/resultado.json'}]],
  use:{baseURL:process.env.UML_E2E_URL ?? 'http://127.0.0.1:5174',
    browserName:'chromium',channel:process.env.UML_E2E_BROWSER ?? 'chrome',
    headless:true,viewport:{width:1440,height:1000},screenshot:'only-on-failure',trace:'off'}
});
