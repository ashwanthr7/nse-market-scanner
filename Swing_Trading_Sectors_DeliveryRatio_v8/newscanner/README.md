# NSE Market Scanner

Separate native Android app using the same visual style as NSE Delivery Ratio.

- NSE official 22-sector selection
- NSE Market Lens query import for Debt/Equity, Interest Coverage, ROCE and Pledge
- NSE sector classification fetched separately from NSE quote data and cached
- Shared NSE daily bhavcopy cache for Delivery Ratio calculations
- Built-in ratio groups and copy buttons
- Healthcare and Oil, Gas & Consumable Fuels OFF by default

Screener's official supported export is used rather than an unofficial scraping/API implementation.


Query input note: the app normalizes spaces around comparison operators before submitting, while preserving the text shown in the query box. It also rejects fields that the current NSE Market Lens query builder does not expose.


Sector filtering v8: NSE quote requests are session-primed and read the official `industryInfo.sector` field directly, with retries for temporary 401/403/429 responses.
