# Security Notes

Publisher/game code should contain only the game-specific `appCode` and `sdkPublicKey` supplied by PocketsFull.

Never put any of the following in the Android or Unity client:

- survey provider key;
- provider postback token;
- Supabase service-role key;
- server-side signing secrets.

Survey rewards are determined from the server-side survey result, not from WebView content or a client-side success screen.

If you believe credentials or reward logic have been exposed, rotate the game credentials and contact the PocketsFull integration team before continuing production traffic.
