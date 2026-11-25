# DIP — पूरा सेटअप (फ़ोन गुम हो जाए तो यहीं से सब वापस)

यह पेज DIP के मालिक (GitHub: thakurdip881-cloud) के सारे ऐप, साइट, कोड और डेटा की पूरी जानकारी है। इसमें कोई पासवर्ड, Token या गुप्त चाबी नहीं है — वे सिर्फ़ मालिक के पास हैं।

**किसी भी AI (ChatGPT, Claude, Gemini) को यह लिंक देकर लिखें:**
> "यह मेरा DIP सिस्टम है। इस पेज को पूरा पढ़ो और मुझे आसान हिंदी में बताओ कि नए फ़ोन में सब कैसे वापस लाऊँ, फिर आगे का काम जारी रखो।"

पेज का लिंक: https://github.com/thakurdip881-cloud/master-connector/blob/main/RECOVERY.md

---

## 1. ऐप (नाम · काम · APK)

| नंबर | छोटा नाम | ऐप | किसके लिए | APK |
|---|---|---|---|---|
| 1 | MC | MASTER CONNECTOR | मालिक, एडमिन, वर्कर — ड्यूटी, हाज़िरी, आदेश, वर्कर, 🎙️ AI | https://github.com/thakurdip881-cloud/master-connector/releases/latest/download/MasterConnector.apk |
| 2 | P30 | 30% Partner | सिर्फ़ मालिक — निजी AI, 📥 बॉक्स, 🔴 लाइव काम, 🔗 सारे लिंक, Claude को काम | https://thakurdip881-cloud.github.io/master-connector/apps/30-Partner.apk |
| 3 | SH | DIP सहायक | सिर्फ़ मालिक — याद (अलार्म), रिपोर्ट, हुक्म | https://thakurdip881-cloud.github.io/master-connector/apps/DIP-Sahayak.apk |
| 4 | SF | DIP सफ़ाई | सिर्फ़ मालिक — फ़ोन और Gmail सफ़ाई | https://thakurdip881-cloud.github.io/master-connector/apps/DIP-Safai.apk |
| 5 | DMA | DIP Master AI | ग्राहक — AI ऐप, 7 दिन ट्रायल, प्लान | https://thakurdip881-cloud.github.io/master-connector/dip/DIP-Master-AI.apk |
| 7 | TOUR | टूर ग्राहक ऐप | ग्राहक (मालिक खुद संभालते हैं) | https://github.com/thakurdip881-cloud/master-connector/releases/download/tour-apk-v1/DipTour.apk |

सबसे नए संस्करण की सूची: https://thakurdip881-cloud.github.io/master-connector/apps/versions.json

## 2. साइटें

| छोटा नाम | साइट | लिंक |
|---|---|---|
| MC | MASTER CONNECTOR वेब | https://thakurdip881-cloud.github.io/master-connector/ |
| DMA | DIP Master AI साइट | https://thakurdip881-cloud.github.io/master-connector/dip/ |
| DCP | DIP Control Panel | https://thakurdip881-cloud.github.io/master-connector/dip/admin.html |
| TOUR | टूर ग्राहक साइट | https://thakurdip881-cloud.github.io/master-connector/tour/ |
| TRN | ट्रेनिंग साइट | https://github-netlify-deploy-0cc412.netlify.app/ |
| — | Firebase नियम पेज | https://thakurdip881-cloud.github.io/master-connector/rules.html |
| — | सारे लिंक | https://thakurdip881-cloud.github.io/master-connector/links.html |

## 3. कोड और डेटा कहाँ है

| छोटा नाम | क्या | कहाँ |
|---|---|---|
| GH-MC | MC वेब + APK, DMA साइट, टूर साइट, Firebase नियम, सारे public APK | https://github.com/thakurdip881-cloud/master-connector (public) |
| GH-DIP | P30, SH, SF, DMA का मूल code, काम की सूची NEXT_UPDATE.md, रिपोर्टें | https://github.com/thakurdip881-cloud/Dip (private — मालिक के GitHub लॉगिन से) |
| — | DMA की ON/OFF सेटिंग | https://github.com/thakurdip881-cloud/dip-config |
| FB | सारा डेटा: वर्कर, KYC, ड्यूटी, आदेश, रिपोर्ट, फ़ाइलें, ग्राहक, P30 का ऑनलाइन बैकअप | Firebase प्रोजेक्ट `master-connector-f2a0d` — https://console.firebase.google.com/project/master-connector-f2a0d |

डेटा फ़ोन में नहीं, Firebase में है — फ़ोन गुम होने पर भी सुरक्षित रहता है।

## 4. नए फ़ोन में सब वापस (कदम-दर-कदम)

1. Chrome में मालिक के Gmail से लॉगिन करें (GitHub और Firebase उसी से खुलते हैं)।
2. ऊपर की तालिका से MC, P30, SH, SF के APK डाउनलोड करके Install करें।
3. **MC:** मालिक का Gmail + पासवर्ड से लॉगिन। पासवर्ड भूले → लॉगिन पेज पर Gmail लिखें → "रीसेट लिंक भेजें"।
4. **GitHub Token (P30 और SH के लिए):** https://github.com/settings/personal-access-tokens/new → नाम DIP, Repository: Dip, Contents: Read and write → Generate → P30 और SH के ⚙️ सेटिंग में पेस्ट।
5. **P30:** ⚙️ में Gemini चाबी (https://aistudio.google.com/apikey से फ़्री) + GitHub Token + "मास्टर कनेक्टर से जोड़ें" → फिर ⚙️ → ☁️ "वापस लाएँ" से पुराना बैकअप।
6. **SH:** ⚙️ में GitHub Token + MC लॉगिन (वर्कर हाज़िरी के लिए)।
7. **SF:** खोलकर अनुमतियाँ दें; Gmail सफ़ाई के लिए ऐप में दिए कदम।
8. वर्करों को कुछ नहीं करना — उनका डेटा Firebase में है। पासवर्ड चाहिए तो MC → 👥 वर्कर → 🔑।

## 5. अपने-आप चलने वाले काम

| नाम | कब | क्या |
|---|---|---|
| HC | रोज़ 9:11, 2:11, 7:11 | GitHub Health check — 15 पेज/APK की जाँच |
| C3 | रोज़ 9:12, 2:12, 7:12 | Claude scheduled task — रिपोर्ट, ऑटो-ठीक, P30/SH से आए हुक्म |
| C2 | हर 2 घंटे | Claude — पेंडिंग काम की याद |
| — | रात 2:30 / 3:00 | SF — पावर सफ़ाई / Gmail सफ़ाई |
| — | रोज़ 3:33 | 3 महीने से पुराना डेटा अपने-आप साफ़ |

## 6. हमेशा के नियम

- सिर्फ़ फ़्री सेवाएँ: GitHub Pages, GitHub Actions, Firebase Spark (फ़्री)। Netlify के credits खत्म — इस्तेमाल नहीं।
- नया ऐप नहीं बनाना — हर बदलाव उसी मौजूदा ऐप में।
- डेटा कम खर्च हो।
- पासवर्ड, Token, चाबी कभी किसी फ़ाइल, chat या रिपोर्ट में नहीं।

_आख़िरी अपडेट: 25-11-2026_
