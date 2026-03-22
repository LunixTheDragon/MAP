# 💬 Cricket Client - Kompletní manuál pro vývojáře a uživatele

Vítejte! Tento repozitář obsahuje zdrojový kód pro klientskou část šifrovaného chatu Cricket. Níže najdete podrobný návod, jak si projekt otevřít, nastavit a úspěšně spustit.

## KROK 1: Stažení a otevření kódu
Abychom mohli aplikaci spustit a případně upravovat, budeme potřebovat vývojové prostředí.
1. Stáhněte a nainstalujte si **IntelliJ IDEA Community Edition** (je zdarma).
2. Spusťte program, zvolte **File -> Open...** a vyberte složku s tímto staženým klientským projektem.

## KROK 2: Co dělat, když to nefunguje (Nastavení Javy)
Pokud po otevření vidíte v kódu spoustu červených chyb, pravděpodobně vám nesedí verze Javy. Projekt je stavěný na moderní **Javě 22**. Jak to rychle opravit:
1. V horním menu klikněte na **File -> Settings**.
2. Vlevo se proklikejte přes **Build, Execution, Deployment -> Build Tools -> Maven**.
3. Zkontrolujte políčka **User settings file** a **Local repository**. Pokud v nich vidíte cizí cestu (např. *C:\Users\ludvi\\.m2...*), zaškrtněte u nich políčko **Override** a přepište složku na vaše vlastní uživatelské jméno ve Windows (případně Override úplně odškrtněte, ať si to systém najde sám).
4. Klikněte na **Apply** a **OK**. Následně vpravo nahoře v panelu Maven klikněte na ikonku **Reload All Maven Projects** (dvě šipky do kruhu). je nastaven na úroveň odpovídající Javě 22. Potvrďte tlačítkem OK.

## KROK 3: Jak aplikaci spustit (Launcher)
Aplikace využívá grafické rozhraní JavaFX, takže se nespouští přes obyčejný soubor `Main`.
1. V levém stromu souborů si rozbalte složky: `src/main/java/graphics/`.
2. Najděte soubor s názvem **`Launcher.java`**.
3. Klikněte na něj pravým tlačítkem myši a zvolte **Run 'Launcher.main()'**. Mělo by na vás vyskočit grafické přihlašovací okno!

## KROK 4: Připojení k serveru a IP adresy
Na přihlašovací obrazovce musíte zadat IP adresu běžícího Cricket Serveru. Co tam napsat?
* Nejprve musíte spustit Cricket server a zapamatovat si na jaké IP adrese běží, to poté vyplnit. 
* **Testuji na jednom PC:** Zadejte `127.0.0.1` (localhost).
* **Připojení v domácí síti:** Zadejte lokální IP adresu počítače se serverem (např. `192.168.1.50`).
*(Aplikace si naposledy zadanou adresu pamatuje, takže ji nemusíte psát pokaždé.)*

**⚠️ POZOR NA FIREWALL (Povolení v síti):**
Pokud se připojujete z jiného počítače a aplikace hlásí, že se nelze připojit, problém je na 99 % ve Windows Firewallu na počítači *se Serverem*. Musíte tam v nastavení firewallu vytvořit **Nové příchozí pravidlo pro port 14000 (TCP)** a komunikaci povolit.

## KROK 5: Kde se ukládají má data? (Systémové složky)
Vaše zprávy a soukromé klíče se neukládají přímo do složky s kódem, ale do chráněné složky vašeho profilu ve Windows.
Složku otevřete tak, že stisknete klávesy `Windows + R` a napíšete:
**`%APPDATA%\Cricket`**

**Zde najdete:**
1. **`server_ip.txt`** - Obyčejný textový soubor s naposledy zadanou IP adresou.
2. **`[VašeJméno]_secure.jceks`** - **Váš nejdůležitější soubor!** Jedná se o osobní, heslem chráněný trezor, ve kterém jsou uloženy vaše šifrovací klíče. Pokud tento soubor ztratíte nebo smažete, už nikdy nedešifrujete své staré zprávy. Pokud přecházíte na jiný počítač, musíte si tento soubor vzít s sebou.
