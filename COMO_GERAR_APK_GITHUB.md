# Gerar o APK no GitHub (sem Android Studio)

Este projeto já traz o workflow automático de build em `.github/workflows/gerar-apk.yml`.

1. Acesse https://github.com/new e crie um repositório (por exemplo, MiraBilharAndroid).
2. Entre no repositório e escolha **Add file > Upload files**.
3. Extraia o ZIP. Abra a pasta `MiraBilharAndroid` e envie **o conteúdo** dela, incluindo a pasta oculta `.github`. As pastas `app` e `.github` e os arquivos `settings.gradle` e `build.gradle` devem aparecer na RAIZ do repositório. Não faça upload do ZIP sozinho.
4. Se o navegador não conseguir incluir a pasta oculta `.github`, crie no repositório o arquivo `.github/workflows/gerar-apk.yml` copiando o conteúdo do arquivo com esse nome do projeto.
5. Confirme o envio (Commit changes) na branch `main` (ou `master`).
6. Abra a aba **Actions**. Se houver solicitação, permita a execução de workflows do repositório.
7. Clique em **Gerar APK Android 12** e acompanhe a execução. Também pode usar **Run workflow** para iniciar manualmente.
8. Se a execução ficar verde, abra a execução e, em **Artifacts**, baixe `MiraBilhar-Android12-APK`.
9. Extraia o ZIP do artefato e encontre `app-debug.apk`; transfira ao celular e instale.

OBSERVAÇÕES:
- Este é um APK de DEBUG, assinado com chave de teste para instalação; não é pacote de publicação.
- A compilação não foi executada neste ambiente; o GitHub fará a primeira compilação real. Se o job falhar, abra o log em `Compilar APK de teste` para corrigir a causa.
- É necessário aceitar no celular a permissão de sobreposição e a captura de tela.
- O algoritmo de mira é experimental e não foi testado em aparelho real; resultado não garantido.
