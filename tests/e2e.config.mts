// Android E2E config for Hellboy VPN. Set APK_PATH to an installable build (CI uses the debug APK).
import type { E2EConfig } from 'e2e';
import { mobile } from '@e2e-dev/mobile';

export default {
  tests: ['flows/**/*.e2e.ts'],
  targets: [
    {
      name: 'android',
      engine: mobile({ platform: 'android' }),
      app: {
        bundleId: 'com.helboy.vpnclub',
        appPath: process.env.APK_PATH || '../app/build/outputs/apk/debug/app-debug.apk',
      },
    },
  ],
} satisfies E2EConfig;
