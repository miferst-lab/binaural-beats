# Binaural Waves — kroki w Google Play Console (dla Daniela)

Plik do wgrania: `/workspace/binaural-play/BinauralWaves-1.7.1-release.aab` (wersja 1.7.1, versionCode 15, pakiet `com.miferstlab.binauralbeats`, podpisany kluczem wgrywania).

> Nic nie zostało wgrane do Google Play — wszystkie poniższe kroki wykonujesz Ty (albo prosisz mnie o pomoc przy konkretnym kroku).

## 1. Aplikacja
1. Play Console → **Utwórz aplikację**: nazwa „Binaural Waves”, język domyślny polski, **Aplikacja**, **Bezpłatna**, zaakceptuj deklaracje.
2. Pozostaw włączone **Play App Signing** (pytanie przy pierwszym wgraniu).

## 2. Subskrypcje (Zarabianie → Produkty → Subskrypcje)
Subskrypcję można utworzyć dopiero, gdy w aplikacji jest wgrany pakiet z uprawnieniem BILLING (nasz AAB je zawiera) — **najpierw wykonaj krok 3 (wgranie AAB), potem wróć tutaj.**
1. **Utwórz subskrypcję**, ID produktu dokładnie: `binaural_premium`, nazwa np. „Binaural Waves Premium”.
2. **Dodaj plan podstawowy** o ID `monthly`: odnawiany automatycznie, okres rozliczeniowy **1 miesiąc**, cena **14,99 PLN** (reszta krajów — wg przeliczenia Play, sprawdź i zatwierdź).
3. **Dodaj plan podstawowy** o ID `yearly`: odnawiany automatycznie, okres **1 rok**, cena **99,99 PLN**.
4. Na każdym planie → **Dodaj ofertę** → kwalifikowalność **„Nowi klienci”** (new customer acquisition) → faza: **Bezpłatny okres próbny, 7 dni**. (ID oferty dowolne, np. `trial7d`.)
5. **Aktywuj** oba plany podstawowe i obie oferty. Aplikacja pobiera ceny z Play, nic nie jest wpisane na sztywno.

## 3. Test zamknięty
1. **Testy i publikacja → Testy → Testy zamknięte** → **Utwórz ścieżkę** (np. „Testerzy”) / użyj domyślnej.
2. **Testerzy** → utwórz listę e-mailowych (do 12+ adresów Gmail/Google; adresy po jednym w wierszu lub CSV). Zapisz.
3. **Wersje → Utwórz nową wersję** → wgraj `BinauralWaves-1.7.1-release.aab` → nazwa wersji np. „1.7.1 test zamknięty” → uwagi o wersji (PL/EN) → **Zapisz → Przejrzyj → Rozpocznij wdrażanie** na test zamknięty.
4. Uzupełnij zadania „Konfiguracja aplikacji” (polityka prywatności: https://miferst-lab.github.io/binaural-beats/, Data safety, klasyfikacja treści, grupa docelowa, reklamy: brak, usługa pierwszoplanowa: odtwarzanie multimediów, aplikacja zdrowotna: to nie wyrób medyczny) — szczegóły i teksty w `/workspace/binaural-play-checklist.md` i `/workspace/binaural-play-paste-pack.md`.
5. Po zatwierdzeniu wersji skopiuj **link opt-in** (karta Testerzy → „Jak dołączyć”) i wyślij testerom razem z `INSTRUKCJA_TESTERZY.md`.

## 4. Testerzy licencji (WAŻNE, żeby nikt nie zapłacił naprawdę)
**Ustawienia (Settings) → Testowanie licencji (License testing)** → dodaj **wszystkie** adresy testerów (oraz swój d.kalinovski@gmail.com). Tylko takie konta robią zakup testowy za darmo. Inni testerzy w teście zamkniętym byliby obciążeni realnie.

## 5. Wymóg 12 testerów × 14 dni (konta osobiste)
1. Min. **12 testerów** musi być zapisanych na test zamknięty **nieprzerwanie 14 dni** (licznik ruszy po tym, jak 12 osób się zapisze — zapisanie = klik „Zostań testerem”). Licz zapas: 15–20 osób.
2. Poproś, by testerzy faktycznie otwierali aplikację w tym czasie (ocena zaangażowania).
3. Po 14 dniach: **Pulpit → „Złóż wniosek o dostęp do wersji produkcyjnej”** (Apply for production) → odpowiedz na pytania (jak rekrutowałeś testerów, jaką opinię zebrałeś, co poprawiłeś, gotowość do produkcji). Odpowiedź Google zwykle do ok. 7 dni.
4. Po akceptacji: **Produkcja → Utwórz wersję** → ten sam lub nowszy AAB → wdrożenie. (Zwiększ versionCode przy każdej nowej wersji.)

## 6. Pozyskanie testerów
Reddit: r/AndroidClosedTesting, r/TestMyApp (wymiana „ja testuję Twoją, Ty moją”) plus rodzina i znajomi. Zbieraj ich adresy Google i dodawaj do listy testerów **oraz** testerów licencji.

## 7. Kopia klucza wgrywania (jeśli jeszcze nie zrobiona)
Zrób offline kopię `binaural-upload.jks` i `credentials.txt` — lokalizacja w `/workspace/binaural-play-checklist.md` (sekcja 5).
