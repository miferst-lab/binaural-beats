# Privacy Policy — Binaural Waves / Polityka prywatności — Binaural Waves

**Last updated / Ostatnia aktualizacja:** 10 October 2026 / 10 października 2026
**Developer / Deweloper:** miferst-lab
**App / Aplikacja:** Binaural Waves (`com.miferstlab.binauralbeats`), version / wersja 1.6.0
**Effective date / Obowiązuje od:** 10 October 2026 / 10 października 2026

Polski tekst znajduje się poniżej / the Polish text follows the English one.

---

# English

## Summary
The developer does **not collect, receive, transmit or sell** any personal data. There are no accounts, no advertising and no analytics or tracking SDKs. All tones and ambient sounds are bundled in the app and play offline. The only network use is Google Play Billing, which talks to Google Play to manage the Premium subscription.

## What is stored on your device
Stored locally only (Android app-private storage, never sent to the developer):
- appearance (theme) setting, selected ambient sound and ambient volume;
- the date the 7-day trial started (and a clock safeguard value used to detect clock changes);
- the last known Premium status (whether Google Play reported an active subscription).

Android's own backup (`allowBackup`) may include this app-private data in your Google/device backup; this is handled by Android and Google, not by the developer. Uninstalling the app removes the local data.

## Subscriptions and payments
Premium is a subscription (monthly or yearly; a 7-day free-trial offer may apply). Payment, billing, renewals, cancellation and refunds are handled entirely by Google Play, so the developer never sees or stores your payment details. You can cancel any time in Google Play. The app uses the Google Play Billing library to check whether your subscription is active, and keeps the result on the device. This communication is between the app and Google Play and is governed by [Google's Privacy Policy](https://policies.google.com/privacy).

## No advertising, no analytics
The app contains no ad SDK, analytics, crash-reporting or tracking library, and does not send audio or personal data to the developer's servers (the developer operates none for this app). The only network-capable component in the app is the Google Play Billing library; the library bundles Google's own data-transport components, so Google may receive technical information from it under Google's policy; we cannot see or control that.

## Permissions (as in the release manifest)
- **INTERNET** and **ACCESS_NETWORK_STATE** — added by the Google Play Billing library so the app can reach Google Play to verify and manage the subscription and check whether a connection is available. The app code itself makes no other network requests.
- **com.android.vending.BILLING** — to start and check the Premium subscription through Google Play.
- **FOREGROUND_SERVICE** and **FOREGROUND_SERVICE_MEDIA_PLAYBACK** — a foreground service of type media playback keeps tones and ambient sounds playing with the screen off.
- **WAKE_LOCK** — keeps the CPU awake while audio is playing so playback is not interrupted.
- **POST_NOTIFICATIONS** — optional; shows the playback notification with a Stop action. Playback works if you deny it.

The app does not request access to contacts, location, camera, microphone, photos, files, phone or health data.

## Children
The app is not directed at children under 13 and does not profile users.

## Contact
Questions: [miferst@pm.me](mailto:miferst@pm.me), or the repository [github.com/miferst-lab/binaural-beats](https://github.com/miferst-lab/binaural-beats) (Issues).

Changes to this policy will be published in this file and on the policy page.

---

# Polski

## Podsumowanie
Deweloper **nie zbiera, nie otrzymuje, nie przesyła i nie sprzedaje** żadnych danych osobowych. Nie ma kont, reklam ani analityki czy trackerów. Wszystkie tony i dźwięki tła są wbudowane w aplikację i działają offline. Jedyne użycie sieci to Google Play Billing, który łączy się z Google Play, aby obsłużyć subskrypcję Premium.

## Co jest zapisane na urządzeniu
Tylko lokalnie (prywatna pamięć aplikacji, nigdy nie wysyłane do dewelopera):
- wygląd (motyw), wybrany dźwięk tła i jego głośność;
- data rozpoczęcia 7-dniowego okresu próbnego (oraz wartość zabezpieczająca przed cofnięciem zegara);
- ostatni znany status Premium (czy Google Play zgłosiło aktywną subskrypcję).

Kopia zapasowa Androida (`allowBackup`) może obejmować te dane w kopii Google/urządzenia; obsługuje to Android i Google, nie deweloper. Odinstalowanie aplikacji usuwa dane lokalne.

## Subskrypcje i płatności
Premium to subskrypcja (miesięczna lub roczna; może obowiązywać 7-dniowy okres próbny). Płatności, rozliczenia, odnowienia, anulowanie i zwroty obsługuje w całości Google Play, więc deweloper nigdy nie widzi ani nie przechowuje danych płatniczych. Subskrypcję możesz anulować w każdej chwili w Google Play. Aplikacja używa biblioteki Google Play Billing do sprawdzenia, czy subskrypcja jest aktywna, i zapisuje wynik na urządzeniu. Ta komunikacja odbywa się między aplikacją a Google Play i podlega [Polityce prywatności Google](https://policies.google.com/privacy?hl=pl).

## Brak reklam i analityki
Aplikacja nie zawiera SDK reklamowych, analitycznych, raportowania awarii ani śledzących i nie wysyła dźwięku ani danych osobowych na serwery dewelopera (deweloper nie prowadzi dla tej aplikacji żadnych serwerów). Jedyny komponent aplikacji zdolny do łączności sieciowej to biblioteka Google Play Billing; biblioteka zawiera własne komponenty transportu danych Google, więc Google może otrzymywać z niej informacje techniczne zgodnie z własną polityką; nie widzimy ani nie kontrolujemy tego.

## Uprawnienia (zgodnie z manifestem wydania)
- **INTERNET** i **ACCESS_NETWORK_STATE** — dodane przez bibliotekę Google Play Billing, aby aplikacja mogła połączyć się z Google Play w celu weryfikacji i obsługi subskrypcji oraz sprawdzić dostępność połączenia. Kod aplikacji nie wykonuje innych żądań sieciowych.
- **com.android.vending.BILLING** — do rozpoczęcia i sprawdzania subskrypcji Premium przez Google Play.
- **FOREGROUND_SERVICE** i **FOREGROUND_SERVICE_MEDIA_PLAYBACK** — usługa pierwszoplanowa typu odtwarzanie multimediów utrzymuje tony i dźwięki tła przy wyłączonym ekranie.
- **WAKE_LOCK** — utrzymuje procesor aktywny podczas odtwarzania, by dźwięk nie był przerywany.
- **POST_NOTIFICATIONS** — opcjonalne; pokazuje powiadomienie odtwarzania z przyciskiem Stop. Odtwarzanie działa też po odmowie.

Aplikacja nie prosi o dostęp do kontaktów, lokalizacji, aparatu, mikrofonu, zdjęć, plików, telefonu ani danych zdrowotnych.

## Dzieci
Aplikacja nie jest skierowana do dzieci poniżej 13. roku życia i nie profiluje użytkowników.

## Kontakt
Pytania: [miferst@pm.me](mailto:miferst@pm.me) lub repozytorium [github.com/miferst-lab/binaural-beats](https://github.com/miferst-lab/binaural-beats) (Issues).

Zmiany tej polityki opublikujemy w tym pliku i na stronie polityki.
