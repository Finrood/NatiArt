export const environment = {
  production: true,
  // Deployments may override this through public/runtime-config.js.
  errorReporting: {url: '/server/directory/client-errors'},
  api: {
    directory: {
      url: '/server/directory',
      endpoints: {
        login: '/login',
        logout: '/signout',
        refreshToken: '/refresh-token',
        registerUser: '/register-user',
        current: '/current',
        user: '/users',
      }
    },
    product: {
      url: '/server/product',
      endpoints: {
        category: '/categories',
        package: '/packages',
        product: '/products',
        order: '/orders',
      }
    },
    viaCep: {
      url: 'https://viacep.com.br/ws',
    },
  }
};
