# TRADER AI 4X Overlay (Android)

Projeto auxiliar Android para exibir um painel flutuante sobre outros aplicativos.

## Estado desta primeira estrutura
- APK auxiliar separado do site.
- WebView abre o TRADER AI 4X hospedado.
- Solicita permissão Android "Exibir sobre outros apps".
- Serviço de sobreposição com sinal, força, detalhe e botões de decisão.
- Não executa operações nem interage automaticamente com o Bullex.

## Construção
Abra esta pasta no Android Studio (JDK 17 e Android SDK 35) e gere o APK em Build > Build APK(s).

## Próximos ajustes necessários
1. Integrar os callbacks JavaScript da página para atualizar o painel com o sinal real.
2. Refinar a área arrastável para não bloquear os botões.
3. Salvar decisões e resultados localmente.
4. Testar em Android real e ajustar comportamento de serviço/permissões conforme a versão do Android.
