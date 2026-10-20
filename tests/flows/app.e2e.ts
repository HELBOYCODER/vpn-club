import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

// These flows use only the test tags of the app (no model calls), so they run with no API key.

test('opens on the home screen with the connect button', async ({ app, screen }) => {
  await app.open();
  await expect(screen.getByTestId('connect_button')).toBeVisible();
});

test('bottom navigation opens servers, scanner and settings', async ({ app, screen }) => {
  await app.open();

  await screen.getByTestId('nav_servers').tap();
  await expect(screen.getByTestId('screen_servers')).toBeVisible();

  await screen.getByTestId('nav_scanner').tap();
  await expect(screen.getByTestId('screen_scanner')).toBeVisible();

  await screen.getByTestId('nav_settings').tap();
  await expect(screen.getByTestId('screen_settings')).toBeVisible();

  await screen.getByTestId('nav_home').tap();
  await expect(screen.getByTestId('connect_button')).toBeVisible();
});
